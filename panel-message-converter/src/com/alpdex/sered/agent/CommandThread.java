package com.alpdex.sered.digicon.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import com.alpdex.infrastructure.exception.BusinessException;
import com.alpdex.infrastructure.exception.BusinessExceptionEJB;
import com.alpdex.sered.allocation.vo.AllocationTimeOptionVO;
import com.alpdex.sered.allocation.vo.AllocationTimeOptionsForParkingmeterVO;
import com.alpdex.sered.customer.exception.NoCreditAvailableException;
import com.alpdex.sered.customer.exception.UserNotFoundException;
import com.alpdex.sered.customer.user.vo.BalanceVO;
import com.alpdex.sered.digicon.agent.exception.ChecksumException;
import com.alpdex.sered.digicon.agent.exception.MessageException;
import com.alpdex.sered.digicon.agent.exception.PartialMessageException;
import com.alpdex.sered.sales.ParkingMeterDelegate;
import com.alpdex.sered.sales.ParkingMeterProcessDelegate;
import com.alpdex.sered.sales.parkingmeter.enumeration.CommandTypeEnum;
import com.alpdex.sered.sales.parkingmeter.vo.CommandParkmeterVO;
import com.alpdex.sered.sales.parkingmeter.vo.ParkingMeterEventVO;
import com.alpdex.sered.sales.salepoint.vo.SalePointVO;

public class CommandThread extends Thread {
	private final Logger logger = Logger.getLogger(CommandThread.class);
	private Socket socket;
	
	public CommandThread(Socket clientSocket) {
		this.socket = clientSocket;
	}
	
	public void run() {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        
        //incluí o IP do equipamento
        MDC.put("RemoteHost", socket.getRemoteSocketAddress());
        
        byte version = DigiconCommandParser.VERSAO_3;
        
        try {
        	logger.info("Nova conexão estabelecida. Total de threads " + Thread.activeCount());
        	
        	socket.setKeepAlive(true);
        	socket.setSoTimeout(AppServer.TIME_OUT_TO_CHECK_IDLE);
        	socket.setTcpNoDelay(true);
        	        	
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            
            final int MAX_MESSAGE_SIZE = 49;
            
            byte[] messageByte = new byte[MAX_MESSAGE_SIZE]; //tamanho máximo de uma mensagem vinda do parquímetro
            boolean end = false; //indicativo para finalizar a conexão
            int pos = 0; //posição no buffer que stá sendo alimentado
            int currentSize = MAX_MESSAGE_SIZE; //tamanho de mensagem atual, no começo assume a padrão
            byte currentCmd = (byte)0x00; //
            long lastRead = 0;
            while (!end) {
            	Integer readInt = null;
            	try {
            		readInt = inputStream.read();
            	} catch (SocketTimeoutException e) {
            		long idleTime = System.currentTimeMillis() - lastRead;
            		if (readInt == null) {
            			if (idleTime > AppServer.TIME_OUT_IDLE) { 
            				logger.debug("Fechando conexão por timeout. Total idle time:" + idleTime);
            				readInt = -1;
            			} else {
            				continue;
            			}
            		}
            	}
            	lastRead = System.currentTimeMillis();
            	if (readInt == -1) {
            		end = true;
            	} else {
            		byte readByte = readInt.byteValue();
            		
            		//System.out.println("readInt: " + readInt + ", readByte: " + readByte + ", hex: " + Utils.byte2HexStringPrint(new byte[] {readByte}));
            		
            		//define o comando
            		if(pos == 0 && readByte != DigiconCommandParser.SD3) {
            			logger.info("Inicio de mensagem do parquímetro ignorada, iniciando com " + readByte);
            			continue; //Inicio de mensagem inválida, ignorar
            		} else if (pos == 1 && readByte == DigiconCommandParser.CMD_PARK_ID) {
            			currentCmd = DigiconCommandParser.CMD_PARK_ID;
            			currentSize = 6;
            		} else if (pos == 1) {
            			version = readByte;
            		} else if (pos == 3 && currentCmd == (byte)0x00) {
            			currentCmd = readByte;
            			if (readByte == DigiconCommandParser.CMD_EVENT) {
            				
            				//conforme versão 
            				if (version == DigiconCommandParser.VERSAO_1) {
            					currentSize = 35;
            				} else if (version == DigiconCommandParser.VERSAO_3) {
            					currentSize = 37;
            				} else {
            					currentSize = 37; //padrão ser o máximo da versão 3
            				}
            				
            			} else if (readByte == DigiconCommandParser.CMD_CLOCK) {
            				currentSize = 10;
            			} else if (readByte == DigiconCommandParser.CMD_SONDA) {
            				currentSize = 19;
            			} else if (readByte == DigiconCommandParser.CMD_PRICE_OPTIONS) {
            				if(version == DigiconCommandParser.VERSAO_1) {
            					currentSize = 16;
            				} else if (version == DigiconCommandParser.VERSAO_3) {
            					currentSize = 18;
            				} else {
            					currentSize = 18; //padrão ser o máximo da versão 3
            				}
            			} else if (readByte == DigiconCommandParser.CMD_LOAD_BALANCE) {
            				currentSize = 49;
            			} else if (readByte == DigiconCommandParser.CMD_LOAD_ALLOCATION) {
            				currentSize = 31;
            			} else if (readByte == DigiconCommandParser.CMD_AUTHORIZATION) {
            				if(version == DigiconCommandParser.VERSAO_1) {
            					currentSize = 35;
            				} else if (version == DigiconCommandParser.VERSAO_3) {
            					currentSize = 37;
            				} else {
            					currentSize = 37; //padrão ser o máximo da versão 3
            				}
            			} else {
            				currentSize = 37;
            			}
            		} else if(pos == 4 && currentCmd == DigiconCommandParser.CMD_LOAD_BALANCE) {
            			if(readByte == (byte) 0x06) {
        					currentSize = 18;
        				}
            		}
            		
            		//logger.debug(String.format("Byte recebido: %02X", readInt & 0xFF));
            		messageByte[pos] = readByte;
            		
            		if (pos == (currentSize - 1)) {
            			
            			byte[] messageByteAdapt = new byte[currentSize];
            			System.arraycopy(messageByte, 0, messageByteAdapt, 0, currentSize);
            			processMessage(messageByteAdapt, outputStream, version);
            			//zera variáveis
            			messageByte = new byte[MAX_MESSAGE_SIZE]; 
            			currentCmd = (byte)0x00; 
        				pos = 0;
        				continue;
            		}
            		
            		pos++;
            	}
            }
            
        } catch (IOException e) {
        	logger.error("Erro ao ler dados do cliente, irá fechar conexão", e);
        } catch (PartialMessageException e) {
			logger.error("Mensagem Parcial recebida", e);
			writeNAK(outputStream, version);
		} catch (MessageException e) {
			logger.error("Mensagem recebida mal formatada", e);
			writeNAK(outputStream, version);
		} catch (ChecksumException e) {
			logger.error("Mensagem recebida com checksum inválido", e);
			writeNAK(outputStream, version);
		} catch (NamingException e) {
			logger.error("Erro ao se comunicar com o servidor JBOSS", e);
			writeNAK(outputStream, version);
		} finally {
        	if (socket != null) {
        		try {
        			if (inputStream != null) {
        				inputStream.close();
        			}
        			
        			if (outputStream != null) {
        				outputStream.close();
        			}
        			
					socket.close();
				} catch (IOException e) {
					logger.error("Erro ao fechar a conexão", e);
				}
        	}
        	logger.info("Conexão fechada.");
        	// remove o IP do equipamento
			MDC.remove("RemoteHost");
        }
        
    }
	
	public void processMessage(byte[] messageByte, OutputStream outputStream, byte version) throws PartialMessageException, MessageException, ChecksumException, NamingException {
		logger.info("Mensagem recebida, HEX: " + Utils.byte2HexStringPrint(messageByte));

		ParkingMeterDelegate delegate = new ParkingMeterDelegate(AppServer.SERED_JNDI);

		List<SalePointVO> listParkingMeterEnable = delegate.listParkingMeterEnable();

		CommandParkmeterVO commandParkmeterVO = DigiconCommandParser.parseBytesToCommandParkmeter(messageByte, listParkingMeterEnable);

		if (commandParkmeterVO == null) {
			return; // do nothing
		}

		if (CommandTypeEnum.PARKINGMETER_ID.equals(commandParkmeterVO.getCommandType())) {
			// define o nome da Thread com o id do parquímetro
			Thread.currentThread().setName(commandParkmeterVO.getParkmeterCode());

			// Informações setadas manualmente pois no banco não pode ser nula e para este tipo de evento não é enviado.
			commandParkmeterVO.setEventDate(new Date());
			commandParkmeterVO.setVersion("-1");

			delegate = new ParkingMeterDelegate(AppServer.SERED_JNDI);
			delegate.createParkingMeterEvent(commandParkmeterVO);

			return;
		}

		// quando solicitado ajuste de horário, enviar mensagem com horário
		if (CommandTypeEnum.CLOCK.equals(commandParkmeterVO.getCommandType())) {
			this.writeClock(outputStream);
			logger.info("Resposta de clock enviada, finalizado.");
			return;
		}

		List<CommandTypeEnum> needProcessing = new ArrayList<CommandTypeEnum>();
		needProcessing.addAll(Arrays.asList(CommandTypeEnum.values()));
		needProcessing.remove(CommandTypeEnum.LOAD_BALANCE);
		needProcessing.remove(CommandTypeEnum.LOAD_ALLOCATION);
		needProcessing.remove(CommandTypeEnum.LOAD_PRICE_OPTIONS);

		Integer eventId = null;
		// chamar serviço para gravar evento
		if (commandParkmeterVO.getCommandType() == null || needProcessing.contains(commandParkmeterVO.getCommandType())) {
			eventId = delegate.createParkingMeterEvent(commandParkmeterVO);
		}

		boolean isOnline = true;
		try {
			isOnline = this.processOnlineCommand(commandParkmeterVO, outputStream, eventId, version);
			if (!isOnline) {
				if (CommandTypeEnum.SONDA.equals(commandParkmeterVO.getCommandType())) {
					this.writeSondaAck(outputStream, Integer.valueOf(commandParkmeterVO.getParkmeterCode()), version);
					logger.info("Resposta de sonda enviada, finalizado.");
				} else {
					this.writeACK(outputStream, commandParkmeterVO.getSequence().shortValue(), version);
					logger.info("Resposta enviada, finalizado.");
				}
			}
		} catch (BusinessExceptionEJB e) {
			logger.error(e.getMessage(), e);
			this.writeNAK(outputStream, version);
			try {
				if (eventId != null) {
					delegate.markerToProcessed(eventId, e.getMessage());
				}
			} catch (NamingException e1) {
				logger.error(e1.getMessage(), e1);
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			this.writeNAK(outputStream, version);
			try {
				if (eventId != null) {
					delegate.markerToProcessed(eventId, e.getMessage());
				}
			} catch (NamingException e1) {
				logger.error(e1.getMessage(), e1);
			}
		}

		// chama serviço para processar pendências.
		if (!isOnline) {
			AppServer.process();
		}

		logger.info("Resposta enviada, finalizado.");
	}

	private boolean processOnlineCommand(CommandParkmeterVO commandParkmeterVO, OutputStream outputStream, Integer eventId, byte version) throws NamingException, BusinessException, BusinessExceptionEJB {

		boolean isOnline = false;

		// Serviços online com resposta ao parquímetro
		if (CommandTypeEnum.LOAD_BALANCE.equals(commandParkmeterVO.getCommandType())) {
			ParkingMeterDelegate delegate = new ParkingMeterDelegate(AppServer.SERED_JNDI);

			BalanceVO balance = null;

			try {
				if (commandParkmeterVO.getUserFederalId() != null) {
					balance = delegate.loadUserBalanceByFederalIdForParkingmeter(commandParkmeterVO.getUserFederalId(), commandParkmeterVO.getUserPassword(), commandParkmeterVO.getPlate());
				} else {
					balance = delegate.loadUserBalanceByPrePaidCardCodeForParkingmeter(commandParkmeterVO.getPrePaidCardCode());
				}

				if (balance.getUserAlpuid() == null) {
					throw new UserNotFoundException();
				}

				int userId = 0;
				int creditBalance = 0;

				if (balance != null && balance.getUserAlpuid() != null) {
					creditBalance = Utils.parseFloatToInt(balance.getCreditBalance(), 2);
					userId = balance.getUserAlpuid();
				}

				if (creditBalance > 0) {
					this.writeBalance(outputStream, userId, creditBalance, commandParkmeterVO.getVersion());
				} else {
					if (version == 4) {
						this.writeNAKWithErrorCode(outputStream, version, DigiconCommandParser.ERROR_UNAVAILABLE_BALANCE);
					} else {
						this.writeBalance(outputStream, 0, 0, commandParkmeterVO.getVersion());
					}
				}
			} catch (UserNotFoundException e) {
				if (version == 4) {
					this.writeNAKWithErrorCode(outputStream, version, DigiconCommandParser.ERROR_UNAVAILABLE_BALANCE);
				} else {
					this.writeBalance(outputStream, 0, 0, commandParkmeterVO.getVersion());
				}
			}
			isOnline = true;
		} else if (CommandTypeEnum.LOAD_PRICE_OPTIONS.equals(commandParkmeterVO.getCommandType())) {

			Calendar initialTime = null;

			ParkingMeterDelegate delegate = new ParkingMeterDelegate(AppServer.SERED_JNDI);
			AllocationTimeOptionsForParkingmeterVO wrapper = delegate.loadPriceOptionsForParkingmeterAllocation(commandParkmeterVO.getPlate(), commandParkmeterVO.getVacancyNumber(), commandParkmeterVO.getVehicleType(), commandParkmeterVO.getParkmeterCode(), commandParkmeterVO.getEventDate(), true);
			List<AllocationTimeOptionVO> timeOptions = wrapper.getAllocationTimeOptionList();

			if (wrapper.getNextInitialTime() != null) {
				initialTime = Calendar.getInstance();
				initialTime.setTime(wrapper.getNextInitialTime());
			}

			this.writePriceOptions(commandParkmeterVO.getVersion(), outputStream, timeOptions, initialTime);

			isOnline = true;
		} else if (CommandTypeEnum.AUTHORIZATION.equals(commandParkmeterVO.getCommandType()) || CommandTypeEnum.AUTHORIZATION_REGULARIZATION.equals(commandParkmeterVO.getCommandType())) {
			try {
				boolean validate = commandParkmeterVO.getValue() != null && commandParkmeterVO.getValue() > 0f;
				if (CommandTypeEnum.AUTHORIZATION.equals(commandParkmeterVO.getCommandType()) && (commandParkmeterVO.getAmount() == null || commandParkmeterVO.getAmount() <= 0)) {
					validate = false;
				}

				if (validate) {
					ParkingMeterProcessDelegate delegate = new ParkingMeterProcessDelegate(AppServer.SERED_JNDI);
					ParkingMeterEventVO processedEventVO = delegate.processParkingMeterEvent(eventId);

					float prePaidCardBalance = processedEventVO.getPrePaidCardBalance() != null ? processedEventVO.getPrePaidCardBalance() : 0f;

					this.writeAuthorization(outputStream, eventId, prePaidCardBalance);
				} else {
					this.writeNAKWithErrorCode(outputStream, version);
				}
			} catch (BusinessExceptionEJB e) {
				logger.error("processOnlineCommand - AUTHORIZATION - " + e.getMessage(), e);

				byte[] errorCode = null;
				if (e.getProblemList().hasAny() && NoCreditAvailableException.class.getSimpleName().equals(e.getProblemList().getProblems().get(0).getCode())) {
					errorCode = DigiconCommandParser.ERROR_UNAVAILABLE_BALANCE;
				}

				// TODO tratar outros erros

				this.writeNAKWithErrorCode(outputStream, version, errorCode);
			} catch (Throwable e) {
				logger.error("processOnlineCommand - AUTHORIZATION - " + e.getMessage(), e);
				this.writeNAKWithErrorCode(outputStream, version); // TODO código de erro
			}

			isOnline = true;
		} else if (CommandTypeEnum.CANCEL_USE.equals(commandParkmeterVO.getCommandType())) {
			try {
				ParkingMeterProcessDelegate delegate = new ParkingMeterProcessDelegate(AppServer.SERED_JNDI);
				delegate.processParkingMeterEvent(eventId);
			} catch (Throwable e) {
				logger.error("processOnlineCommand - CANCEL_USE - " + e.getMessage(), e);
				this.writeNAKWithErrorCode(outputStream, version); // TODO código de erro
			}

			this.writeACK(outputStream, commandParkmeterVO.getSequence().shortValue(), version);

			isOnline = true;
		}

		return isOnline;
	}

	private void writePriceOptions(String version, OutputStream outputStream, List<AllocationTimeOptionVO> timeOptions, Calendar previousAllocationEndTime) {

		byte[] priceOptionsBytes = DigiconCommandParser.buildPriceOptions(version, timeOptions, previousAllocationEndTime);
		
		logger.info("Respondendo LoadPriceOptions, HEX: " + Utils.byte2HexStringPrint(priceOptionsBytes));
		
		try {
			outputStream.write(priceOptionsBytes);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}
	
	private void writeAuthorization(OutputStream outputStream, int eventId, float userCreditBalance) {
		
		byte[] authorizationBytes = DigiconCommandParser.buildAuthorizationResponseForAllocation(eventId, userCreditBalance);
		
		logger.info("Respondendo Authorization, HEX: " + Utils.byte2HexStringPrint(authorizationBytes));
		
		try {
			outputStream.write(authorizationBytes);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}

	public void writeACK(OutputStream outputStream, short message, byte version) {
		
		byte[] ack = DigiconCommandParser.buildACK(message, version);
		
		logger.info("Respondendo ACK, HEX: " + Utils.byte2HexStringPrint(ack));
		
		try {
			outputStream.write(ack);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}
	
	public void writeNAK(OutputStream outputStream, byte version) {
		byte[] nak = DigiconCommandParser.buildNAK(version);
		
		logger.info("Respondendo NAK, HEX: " + Utils.byte2HexStringPrint(nak));
		
		try {
			outputStream.write(nak);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
		
		logger.info("Mensagem NAK enviada");
	}

	public void writeNAKWithErrorCode(OutputStream outputStream, byte version) {
		this.writeNAKWithErrorCode(outputStream, version, new byte[] { (byte) 0x00, (byte) 0x00 });
	}

	public void writeNAKWithErrorCode(OutputStream outputStream, byte version, byte[] error) {
		byte[] nak = DigiconCommandParser.buildNAKWithErrorCode(version, error);
		
		logger.info("Respondendo NAK, HEX: " + Utils.byte2HexStringPrint(nak));
		
		try {
			outputStream.write(nak);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
		
		logger.info("Mensagem NAK enviada");
	}
	
	public void writeClock(OutputStream outputStream) {
		byte[] clock = DigiconCommandParser.buildClock();
		
		logger.info("Respondendo CLOCK, HEX: " + Utils.byte2HexStringPrint(clock));
		
		try {
			outputStream.write(clock);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}
	
	public void writeBalance(OutputStream outputStream, int userId, int balance, String version) {
		
		byte v = DigiconCommandParser.VERSAO_4;
		if("3".equals(version)) {
			v = DigiconCommandParser.VERSAO_3;
		} else if ("1".equals(version)) {
			v = DigiconCommandParser.VERSAO_1;
		}
		
		byte[] balanceBytes = DigiconCommandParser.buildBalance(userId, balance, v);
		
		logger.info("Respondendo LoadBalance, HEX: " + Utils.byte2HexStringPrint(balanceBytes));
		
		try {
			outputStream.write(balanceBytes);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}

	private void writeSondaAck(OutputStream outputStream, int parkmeterCode, byte version) {
		byte dat1 = (byte) ((parkmeterCode >> 8) & 0xFF);
		byte dat2 = (byte) (parkmeterCode & 0xFF);

		byte[] sondaAck = DigiconCommandParser.buildSondaAck(dat1, dat2, version);

		logger.info("Respondendo SONDA ACK, HEX: " + Utils.byte2HexStringPrint(sondaAck));

		try {
			outputStream.write(sondaAck);
		} catch (IOException e) {
			logger.error("Erro ao enviar dados ao cliente", e);
		}
	}
}