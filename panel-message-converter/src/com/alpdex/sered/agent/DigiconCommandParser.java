package com.alpdex.sered.digicon.agent;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.json.me.JSONArray;
import org.json.me.JSONException;
import org.json.me.JSONObject;

import com.alpdex.sered.allocation.enumeration.VacancyAreaTypeEnum;
import com.alpdex.sered.allocation.vo.AllocationTimeOptionVO;
import com.alpdex.sered.digicon.agent.exception.ChecksumException;
import com.alpdex.sered.digicon.agent.exception.MessageException;
import com.alpdex.sered.digicon.agent.exception.PartialMessageException;
import com.alpdex.sered.sales.enumeration.ParkingMeterStatusEnum;
import com.alpdex.sered.sales.enumeration.SalePointStatusEnum;
import com.alpdex.sered.sales.parkingmeter.enumeration.CardBrandEnum;
import com.alpdex.sered.sales.parkingmeter.enumeration.CommandTypeEnum;
import com.alpdex.sered.sales.parkingmeter.vo.CommandParkmeterVO;
import com.alpdex.sered.sales.salepoint.vo.SalePointVO;
import com.alpdex.sered.vehicle.enumeration.VehicleTypeEnum;

public class DigiconCommandParser {
	
	private static final Logger logger = Logger.getLogger(DigiconCommandParser.class);
	public static final byte SD3 = (byte) 0xA2;
	public static final byte VERSAO_1 = (byte) 0x01;
	public static final byte VERSAO_3 = (byte) 0x03;
	public static final byte VERSAO_4 = (byte) 0x04;
	public static final byte ACK = (byte) 0x06;
	public static final byte NAK = (byte) 0x15;
	public static final byte END = (byte) 0x16;
	
	public static final byte CMD_EVENT = (byte)0x4C;            //"L" 4C - evento
	public static final byte CMD_CLOCK = (byte)0x52;            //“R” 52H - acerto de relógio
	public static final byte CMD_PARK_ID = (byte)0x49;          //"I" 49H - comando de identificação do parquímetro
	public static final byte CMD_SONDA = (byte)0x53;   			//"S" 53H - sonda
	public static final byte CMD_LOAD_BALANCE = (byte)0x42;     //"B" 42H - Comando consulta de saldo
	public static final byte CMD_PRICE_OPTIONS = (byte)0x56; 	//"V" 56H - Comando consulta de tarifação
	public static final byte CMD_LOAD_ALLOCATION = (byte)0x54;  //"T" 54H - Comando Alocações
	public static final byte CMD_AUTHORIZATION = (byte)0x41;    //"A" 41H - Comando autorizar alocaçao
	
	public static final byte CONST_VEHICLE_TYPE_CAR = (byte) 0x0A;        //Tipo de veículo: CARRO
	public static final byte CONST_VEHICLE_TYPE_MOTORCYCLE = (byte) 0x0D; //Tipo de veículo: MOTO
	
	public static final byte[] ERROR_INVALID_PLATE = {(byte) 0x01, (byte) 0x90};          //Placa inválida
	public static final byte[] ERROR_INTERNAL_ERROR = {(byte) 0x03, (byte) 0xE7};         //Erro servidor
	public static final byte[] ERROR_INVALID_DOCUMENT = {(byte) 0x01, (byte) 0x95};       //Documento inválido
	public static final byte[] ERROR_INVALID_PASSWORD = {(byte) 0x01, (byte) 0x96};       //Senha inválida
	public static final byte[] ERROR_BLOCKED_ACCOUNT = {(byte) 0x01, (byte) 0x97};        //Conta bloqueada
	public static final byte[] ERROR_UNAVAILABLE_BALANCE = {(byte) 0x01, (byte) 0x98};    //Saldo Insuficiente

	private static final String SUB_COMMAND_PAYMENT_DEBIT_CARD = "43";
	private static final String SUB_COMMAND_PAYMENT_CREDIT_CARD = "44";
	private static final String SUB_COMMAND_VIRTUAL_ACCOUNT_ALLOCATION = "45";

	private static Map<String, DigiconCommandVO> operacaoMap;
	private static Map<String, DigiconCommandVO> subOperacaoMap;
	private static Map<String, DigiconAllocationTypeVO> allocationMap;
	
	static {
		operacaoMap = readDigiconCommandJSON("digicon-operacao.json");
		subOperacaoMap = readDigiconCommandJSON("digicon-sub-operacao.json");
		allocationMap = readDigiconAllocationTypeJSON("digicon-allocation-type.json");
	}
	
	public static CommandParkmeterVO parseBytesToCommandParkmeter(byte[] commandBytes, List<SalePointVO> listParkingMeterEnable) throws PartialMessageException, MessageException, ChecksumException {
		CommandParkmeterVO vo = new CommandParkmeterVO();
		
		//SD3 A2H 
		if (commandBytes[0] != SD3){
			throw new MessageException();
		}
		
		//FIXME remover, apenas para teste
		/*String bytes = "{";
		for(int i= 0; i< commandBytes.length; i++) {
			bytes += bytes.length() > 1 ? "," : "";
			bytes += commandBytes[i];
		}
		bytes += "}";
		System.out.println(bytes);*/
		
		if (commandBytes[1] == CMD_PARK_ID) {
			parseParkimeterId(commandBytes, vo);
			return vo;
		}
		
		//não validamos a versão por enquanto.
		//VERSAO 01H ou 03H
		if (commandBytes[1] != VERSAO_1 && commandBytes[1] != VERSAO_3 && commandBytes[1] != VERSAO_4) {
			logger.warn("Versão recebida diferente da esperada, mas iremos tentar processar. Chegou: "+ String.format("Byte recebido: %02X", commandBytes[1] & 0xFF));
		}
		
		vo.setVersion(Integer.toString(parseInt(commandBytes[1])));
		
		//comando para evento
		//"L" 4C - evento
		if (commandBytes[3] == CMD_EVENT) {
			
			SalePointVO parkingMeterSalePointVO = loadParkingMeterSalePointVO(listParkingMeterEnable, vo);
			parseEvent(commandBytes, vo, parkingMeterSalePointVO);
			
			parseExternalCodes(vo, parkingMeterSalePointVO);		
			logger.debug("Operacao: "+ vo.getExternalCommandDescription());
			logger.debug("Sub-Operacao: "+ vo.getExternalSubCommandDescription());
			logger.debug("Tipo: " + vo.getCommandType());
			return vo;
		//“R” 52H - acerto de relógio
		} else if (commandBytes[3] == CMD_CLOCK) {
			parseClock(commandBytes, vo);
			return vo;
		} else if (commandBytes[3] == CMD_LOAD_BALANCE) {
			parseLoadBalance(commandBytes, vo);
			return vo;
		} else if(commandBytes[3] == CMD_LOAD_ALLOCATION) {
			parseLoadAllocation(commandBytes, vo);
			return vo;
		}else if (commandBytes[3] == CMD_PRICE_OPTIONS) {
			parseLoadPriceOptions(commandBytes, vo);
			return vo;
		} else if(commandBytes[3] == CMD_AUTHORIZATION) {
			SalePointVO parkingMeterSalePointVO = loadParkingMeterSalePointVO(listParkingMeterEnable, vo);
			parseAuthorization(commandBytes, vo, parkingMeterSalePointVO);
			return vo;
		} else if (commandBytes[3] == CMD_SONDA) {
			new DigiconCommandSondaParser().parse(commandBytes, vo);
			return vo;
		}

		return null;
	}

	private static SalePointVO loadParkingMeterSalePointVO(List<SalePointVO> listParkingMeterEnable, CommandParkmeterVO vo) {
		SalePointVO parkingMeterSalePointVO = null;
		if(listParkingMeterEnable != null) {
			for (SalePointVO salePointVO : listParkingMeterEnable) {
				if (salePointVO.getCode().equals(vo.getParkmeterCode())) {
					parkingMeterSalePointVO = salePointVO;
					break;
				}
			}
		}
		return parkingMeterSalePointVO;
	}
	
	private static void parseExternalCodes(CommandParkmeterVO vo, SalePointVO parkingMeterSalePointVO) {
		//mapear evento x commandtype e descrições dos códigos de comando e dos status e seu tipo
		
		String externalCommand = vo.getExternalCommand();
		String subExternalCommand = vo.getExternalSubCommand();
		
		vo.setCommandType(CommandTypeEnum.STATUS);
		if (operacaoMap.containsKey(externalCommand)) {
			DigiconCommandVO digiconCommandVO = operacaoMap.get(externalCommand);
			CommandTypeEnum commandTypeEnum = digiconCommandVO.getCommandType();
			if (commandTypeEnum != null) {
				vo.setCommandType(commandTypeEnum);
			}
			
			SalePointStatusEnum status = digiconCommandVO.getStatus();
			if(status != null) {
				vo.setParkMeterStatus(status);
			}
			
			ParkingMeterStatusEnum statusType = digiconCommandVO.getStatusType();
			if(statusType != null) {
				vo.setParkMeterStatusType(statusType);
			}
			
			vo.setExternalCommandDescription(digiconCommandVO.getDescription());
			vo.setVehicleType(digiconCommandVO.getVehicleType());
			
			parseAllocationTimeType(vo, parkingMeterSalePointVO);
			
		} else {
			vo.setExternalCommandDescription("Comando não mapeado!!!");
		}
		
		if (subOperacaoMap.containsKey(subExternalCommand)) {
			DigiconCommandVO digiconCommandVO = subOperacaoMap.get(subExternalCommand);
			vo.setExternalSubCommandDescription(digiconCommandVO.getDescription());
			if (digiconCommandVO.getCommandType() != null) {
				vo.setCommandType(digiconCommandVO.getCommandType());
			} else if (digiconCommandVO.getCommandTypeVariationMap() != null && digiconCommandVO.getCommandTypeVariationMap().containsKey(vo.getExternalCommand())) {
				DigiconCommandVO subDigiconCommandVO = digiconCommandVO.getCommandTypeVariationMap().get(vo.getExternalCommand());
				if (subDigiconCommandVO.getCommandType() != null) {
					vo.setCommandType(subDigiconCommandVO.getCommandType());
				}
			}
			
			if( digiconCommandVO.getStatus() != null) {
				vo.setParkMeterStatus(digiconCommandVO.getStatus());
			} else if (digiconCommandVO.getStatusVariationMap() != null && digiconCommandVO.getStatusVariationMap().containsKey(vo.getExternalCommand())) {
				DigiconCommandVO subDigiconCommandVO = digiconCommandVO.getStatusVariationMap().get(vo.getExternalCommand());
				if(subDigiconCommandVO.getStatus() != null) {
					vo.setParkMeterStatus(subDigiconCommandVO.getStatus());
				}
			}
			
			if(digiconCommandVO.getStatusType() != null ) {
				vo.setParkMeterStatusType(digiconCommandVO.getStatusType());
			} else if(digiconCommandVO.getStatusTypeVariationMap() != null && digiconCommandVO.getStatusTypeVariationMap().containsKey(vo.getExternalCommand())) {
				DigiconCommandVO subDigiconCommandVO = digiconCommandVO.getStatusTypeVariationMap().get(vo.getExternalCommand());
				if(subDigiconCommandVO.getStatusType() != null) {
					vo.setParkMeterStatusType(subDigiconCommandVO.getStatusType());
				}
			}
		}
	}

	public static void parseAllocationTimeType(CommandParkmeterVO vo, SalePointVO parkingMeterSalePointVO) {
		if (vo.getVehicleType() != null && vo.getValue() != null && vo.getValue() > 0) {
			String area = parkingMeterSalePointVO != null && parkingMeterSalePointVO.getVacancyAreaTypeEnum() != null ? parkingMeterSalePointVO.getVacancyAreaTypeEnum().name() : null;
			String key = vo.getVehicleType().name() + "_" + area + "_" + vo.getValue();
			
			if (!allocationMap.containsKey(key) && area != null) {
				area = null;
				key = vo.getVehicleType().name() + "_" + area + "_" + vo.getValue();
			}
			
			if (allocationMap.containsKey(key)) {
				DigiconAllocationTypeVO digiconAllocationTypeVO = allocationMap.get(key);					
				vo.setTimeTypeId(digiconAllocationTypeVO.getTimeType());
				vo.setAmount(digiconAllocationTypeVO.getAmount());
			}
		}
	}
	
	private static void parseParkimeterId(byte[] commandBytes, CommandParkmeterVO vo) {
		vo.setCommandType(CommandTypeEnum.PARKINGMETER_ID);
		// ******** IDENT_PARK
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[2];
		// DAT2 ident_park - byte menos significativo
		byte DAT2 = commandBytes[3];
		
		int identPark = ((DAT1 & 0xff) << 8) | (DAT2 & 0xff);
		logger.debug("Ident_park:" + identPark);
		vo.setParkmeterCode(Integer.toString(identPark));
		
		
		//CS - checksum – soma de todos os bytes, exceto ED
		//ED - delimitador final: 16H
	}
	
	private static void parseEvent(byte[] commandBytes, CommandParkmeterVO vo, SalePointVO parkingMeterSalePointVO) throws ChecksumException {

		//instancia o parser específico de cada versão
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);
		
		// ******** IDENT_PARK
		int identPark = commandParser.parseParkmeterCode();
		vo.setParkmeterCode(Integer.toString(identPark));

		// ******** Código da Operação
		int operacao = commandParser.parseExternalCommand();
		vo.setExternalCommand(Integer.toString( operacao ));

		// ******** número da operação
		int sequence = commandParser.parseSequence();
		vo.setSequence(sequence);

		// ******** data do evento
		Calendar eventDate = commandParser.parseEventDate();
		vo.setEventDate(eventDate.getTime());
		

		// ********* número serial do cartão ou ID usuário (Pula para fazer depois de identificar a suboperacao)
		int prePaidCardCodePosition = commandParser.getCurrentPosition();
		commandParser.setCurrentPosition(commandParser.getCurrentPosition() + 4);

		// ********* valor da operação
		float valorOperacao = commandParser.parseValue();
		vo.setValue(valorOperacao);

		// ********* codigo da sub-operação
		String subOperacao = commandParser.parseExternalSubCommand();
		vo.setExternalSubCommand(subOperacao);

		// ********* saldo do cartão
		float saldoCartao = commandParser.parsePrePaidCardBalance();
		vo.setPrePaidCardBalance(saldoCartao);
			
		//Faz o parse da sub-operação para verificar se for regularização não irá ler placa e vaga e sim o ID da regularização.
		parseExternalCodes(vo, parkingMeterSalePointVO);
		
		logger.debug("Operacao: "+ vo.getExternalCommandDescription());
		logger.debug("Sub-Operacao: "+ vo.getExternalSubCommandDescription());
		logger.debug("Tipo: " + vo.getCommandType());
		
		int currentPosition = commandParser.getCurrentPosition();
		commandParser.setCurrentPosition(prePaidCardCodePosition);
		if(CommandTypeEnum.REGULARIZATION.equals(vo.getCommandType())) {
			String prePaidCardCode = commandParser.parsePrePaidCardCodeAsNotificationNumber();
			vo.setPrePaidCardCode(prePaidCardCode); //se o código é zero envia null
		}

		if(vo.getPrePaidCardCode() == null && !CommandTypeEnum.REGULARIZATION_PAYMENT.equals(vo.getCommandType())) {
			commandParser.setCurrentPosition(prePaidCardCodePosition);
			if(vo.getExternalSubCommand().equals(SUB_COMMAND_VIRTUAL_ACCOUNT_ALLOCATION)) {
				Integer userId = commandParser.parseUserId();
				vo.setUserAlpuid(userId);
			} else {
				String prePaidCardCode = commandParser.parsePrePaidCardCode();
				vo.setPrePaidCardCode(prePaidCardCode); //se o código é zero envia null
			}
			commandParser.setCurrentPosition(currentPosition);
		}
		commandParser.setCurrentPosition(currentPosition);
		
		if(CommandTypeEnum.REGULARIZATION_PAYMENT.equals(vo.getCommandType())) {
			String idRegularização = commandParser.parseRegularizationID();
			vo.setRegularizationId(idRegularização); //Está sendo utilizado o campo PrePaidCardCode no processamento do Evento como NotificationNumber;
		}
		
		if(vo.getRegularizationId() == null) {
			// ********* placa do veiculo
			String plate = commandParser.parsePlate();
			
			//se a placa não for considerada válida, assume que é formato livre, pois o parquímetro teria de validar.
			if (plate != null && plate.length() > 1 && !Utils.isValidPaneFormat(plate, false)) {
				vo.setIsPlateFreeFormat(true);
			}
			
			vo.setPlate(plate);
	
			// ********* vaga
			Integer vacancy = Integer.valueOf(commandParser.parseVacancyNumber());
			vo.setVacancyNumber(vacancy != null && vacancy > 0 ? vacancy : null);
			
			if(CommandTypeEnum.REGULARIZATION.equals(vo.getCommandType())) {
				vo.setRegularizationId(vo.getPrePaidCardCode());
				vo.setPrePaidCardCode(null);
			}
		}

		currentPosition = commandParser.getCurrentPosition();
		if (SUB_COMMAND_PAYMENT_DEBIT_CARD.equals(vo.getExternalSubCommand()) || SUB_COMMAND_PAYMENT_CREDIT_CARD.equals(vo.getExternalSubCommand())) {
			commandParser.setCurrentPosition(prePaidCardCodePosition);
			String paymentAuthenticator = commandParser.parsePaymentAuthenticator();
			vo.setPaymentAuthenticator(paymentAuthenticator);

			commandParser.setCurrentPosition(commandParser.getCurrentPosition() + 5);
			CardBrandEnum cardBrandEnum = commandParser.parseCardBrand();
			vo.setCardBrandEnum(cardBrandEnum);

			vo.setUserAlpuid(null);
			vo.setPrePaidCardCode(null);
			vo.setPrePaidCardBalance(null);
		}
		commandParser.setCurrentPosition(currentPosition);

		// ********* Tensão da bateria
		int tensaoBateria = commandParser.parseBatteryStatus(); 
		vo.setBatteryStatus(tensaoBateria);

		// ********* Check Sum
		commandParser.checksum();
	}
	
	private static void parseClock(byte[] commandBytes, CommandParkmeterVO vo) throws ChecksumException {
		logger.info("Clock ajust");
		
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);
		
		int identPark = commandParser.parseParkmeterCode();
		vo.setParkmeterCode(Integer.toString(identPark));
		
		commandParser.clockRUF();

		// ********* Check Sum
		commandParser.checksum();
		
		vo.setEventDate(new Date());
		vo.setCommandType(CommandTypeEnum.CLOCK);
	}
	
	private static void parseLoadBalance(byte[] commandBytes, CommandParkmeterVO vo) throws ChecksumException {
		
		logger.info("Load balance");
		
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);
		
		Integer identificationType = commandParser.parseLoadBalanceType();

		// ******** Código da Operação
		int sequence = commandParser.parseSequence();
		vo.setExternalCommand(Integer.toString( sequence ));
		
		// ******** IDENT_PARK
		int identPark = commandParser.parseParkmeterCode();
		vo.setParkmeterCode(Integer.toString(identPark));
		
		if(identificationType != null && identificationType.intValue() == 6) {
			
			String prePaidCardCode = commandParser.parsePrePaidCardCodeMifarePlus();
			vo.setPrePaidCardCode(prePaidCardCode);
			
			logger.info("Consulta saldo: cartão = " + prePaidCardCode);		
		
		} else {
			//CPF/CNPJ USUÁRIO (4-17)
			String federalIdentification = commandParser.parseFederalIdentification();
			vo.setUserFederalId(federalIdentification != null ? federalIdentification.trim() : null);
			
			//SENHA USUÁRIO (18-34)
			String password = commandParser.parseUserPassword(17);
			vo.setUserPassword(password != null ? password.trim() : null);
			
			String plate = commandParser.parsePlate();
			vo.setPlate(plate);
			
			logger.info("Consulta saldo: FederalId = " + (vo.getUserFederalId()) + ", Password = " + (vo.getUserPassword()));			
		}

		// ********* Check Sum
		// CS inverte resultado(SD3+VERSAO+TAM+“R”+DAT1+ ...+DAT4)
//		commandParser.checksum();
		
		vo.setEventDate(new Date());
		vo.setCommandType(CommandTypeEnum.LOAD_BALANCE);
	}
	
	private static void parseLoadAllocation(byte[] commandBytes, CommandParkmeterVO vo) throws ChecksumException {
		
		logger.info("Load Allocation");
				
		//instancia o parser específico de cada versão
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);
		
		int identPark = commandParser.parseParkmeterCode();
		
		String plate = commandParser.parsePlate();
		logger.debug("Plate:" + plate);
		
		Integer vacancy = Integer.valueOf(commandParser.parseVacancyNumber());
		logger.debug("Vacancy:" + vacancy);	
		
		VehicleTypeEnum vehicleTypeEnum = commandParser.parseVehicleType();
		logger.debug("VehicleType: " + vehicleTypeEnum);
		
		commandParser.checksum();
		
		vo.setParkmeterCode(String.valueOf(identPark));
		vo.setPlate(plate);
		vo.setVacancyNumber(vacancy != null && vacancy > 0 ? vacancy : null);
		vo.setVehicleType(vehicleTypeEnum);
		vo.setEventDate(new Date());
		vo.setCommandType(CommandTypeEnum.LOAD_ALLOCATION);		
	}
	
	private static void parseLoadPriceOptions(byte[] commandBytes, CommandParkmeterVO vo) throws ChecksumException {
		
		//instancia o parser específico de cada versão
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);
		
		int identPark = commandParser.parseParkmeterCode();
		
		String plate = commandParser.parsePlate();
		logger.debug("Plate:" + plate);
		
		Integer vacancy = Integer.valueOf(commandParser.parseVacancyNumber());
		logger.debug("Vacancy:" + vacancy);	
		
		VehicleTypeEnum vehicleTypeEnum = commandParser.parseVehicleType();
		logger.debug("VehicleType: " + vehicleTypeEnum);
		
		commandParser.checksum();
		
		vo.setParkmeterCode(String.valueOf(identPark));
		vo.setPlate(plate);
		vo.setVacancyNumber(vacancy != null && vacancy > 0 ? vacancy : null);
		vo.setVehicleType(vehicleTypeEnum);
		vo.setEventDate(new Date());
		vo.setCommandType(CommandTypeEnum.LOAD_PRICE_OPTIONS);
	}
	
	private static void parseAuthorization(byte[] commandBytes, CommandParkmeterVO vo, SalePointVO parkingMeterSalePointVO) throws ChecksumException {

		// instancia o parser específico de cada versão
		CommandParser commandParser = buildCommandParser(commandBytes, vo.getVersion(), 4);

		// ******** IDENT_PARK
		int identPark = commandParser.parseParkmeterCode();
		vo.setParkmeterCode(Integer.toString(identPark));

		// ******** Código da Operação
		int operacao = commandParser.parseExternalCommand();
		vo.setExternalCommand(Integer.toString(operacao));

		// ******** número da operação
		int sequence = commandParser.parseSequence();
		vo.setSequence(sequence);

		// ******** data do evento
		Calendar eventDate = commandParser.parseEventDate();
		vo.setEventDate(eventDate.getTime());

		// ********* número serial do cartão ou ID usuário
		int userOrPrePaidCardPosition = commandParser.getCurrentPosition();
		commandParser.setCurrentPosition(commandParser.getCurrentPosition() + 4);

		// ********* valor da operação
		float valorOperacao = commandParser.parseValue();
		vo.setValue(valorOperacao);

		// ********* codigo da sub-operação
		String subOperacao = commandParser.parseExternalSubCommand();
		vo.setExternalSubCommand(subOperacao);

		// Faz o parse da sub-operação para verificar se for regularização não irá ler placa e vaga e sim o ID da regularização.
		parseExternalCodes(vo, parkingMeterSalePointVO);
		vo.setCommandType(!CommandTypeEnum.REGULARIZATION_PAYMENT.equals(vo.getCommandType()) ? CommandTypeEnum.AUTHORIZATION : CommandTypeEnum.AUTHORIZATION_REGULARIZATION); // Força como AUTHORIZATION pois no EXTERNALCODE define como STATUS pelo JSON
		logger.debug("Operacao: " + vo.getExternalCommandDescription());
		logger.debug("Sub-Operacao: " + vo.getExternalSubCommandDescription());
		logger.debug("Tipo: " + vo.getCommandType());

		int currentPosition = commandParser.getCurrentPosition();
		commandParser.setCurrentPosition(userOrPrePaidCardPosition);
		if (vo.getExternalSubCommand().equals(SUB_COMMAND_VIRTUAL_ACCOUNT_ALLOCATION)) {
			Integer userId = commandParser.parseUserId();
			vo.setUserAlpuid(userId);
		} else {
			String prePaidCardCode = commandParser.parsePrePaidCardCode();
			vo.setPrePaidCardCode(prePaidCardCode); // se o código é zero envia null
		}
		commandParser.setCurrentPosition(currentPosition);

		int allocationTimeInMinutes = commandParser.parseAllocationTimeInMinutes();
		vo.setAmount(allocationTimeInMinutes);

		vo.setTimeTypeId(null); // Ignora o timeType setado no json, irá definir pelo tempo e valor

		if (CommandTypeEnum.AUTHORIZATION_REGULARIZATION.equals(vo.getCommandType())) {
			String idRegularização = commandParser.parseRegularizationID();
			vo.setRegularizationId(idRegularização); // Está sendo utilizado o campo PrePaidCardCode no processamento do Evento como NotificationNumber;
		}

		if (vo.getRegularizationId() == null) {
			// ********* placa do veiculo
			String plate = commandParser.parsePlate();

			// se a placa não for considerada válida, assume que é formato livre, pois o parquímetro teria de validar.
			if (plate != null && plate.length() > 1 && !Utils.isValidPaneFormat(plate, false)) {
				vo.setIsPlateFreeFormat(true);
			}

			vo.setPlate(plate);

			// ********* vaga
			Integer vacancy = Integer.valueOf(commandParser.parseVacancyNumber());
			vo.setVacancyNumber(vacancy != null && vacancy > 0 ? vacancy : null);
		}

		// ********* Tensão da bateria
		int tensaoBateria = commandParser.parseBatteryStatus();
		vo.setBatteryStatus(tensaoBateria);

		vo.setEventDate(new Date());

		// ********* Check Sum
		commandParser.checksum();
	}

	private static CommandParser buildCommandParser(byte[] commandBytes, String version, int startPosition) {
		CommandParser commandParser = null;
		if ("4".equals(version)) {
			commandParser = new DigiconCommandParserV04(commandBytes);			
		} else if ("3".equals(version)) {
			commandParser = new DigiconCommandParserV03(commandBytes);
		} else if ("1".equals(version)) {
			commandParser = new DigiconCommandParserV01(commandBytes);
		} else {
			commandParser = new DigiconCommandParserV01(commandBytes);
		}
		commandParser.setCurrentPosition(startPosition);
		return commandParser;
	}
	
	public static byte[] buildBalance(int userId, int balance, byte version) {
		byte[] messageHeaders = new byte[]{
				DigiconCommandParser.SD3, //SD3
				version, //VERSAO
				(byte) 0x08, //TAMANHO 7 bytes
				DigiconCommandParser.ACK, //ACK
		}; 
		
		byte[] messageEnd = new byte[] {
				(byte) 0x00, //CS checksum alimentado em seguida
				DigiconCommandParser.END //END
		};

		//saldo //DAT1-DAT4
		byte[] messageBody = new byte[8];
		messageBody[0] = (byte) ((balance >> 24) & 0xFF);
		messageBody[1] = (byte) ((balance >> 16) & 0xFF);		
		messageBody[2] = (byte) ((balance >> 8) & 0xFF);
		messageBody[3] = (byte) (balance & 0xFF);

		//ID usuario //DAT5-DAT8
		messageBody[4] = (byte) ((userId >> 24) & 0xFF);
		messageBody[5] = (byte) ((userId >> 16) & 0xFF);
		messageBody[6] = (byte) ((userId >> 8) & 0xFF);
		messageBody[7] = (byte) (userId & 0xFF);

		byte[] result = buidWriteMessage(messageHeaders, messageBody, messageEnd);
		
		return result;
	}
	
	public static byte[] buildPriceOptions(String version, List<AllocationTimeOptionVO> timeOptions, Calendar previousAllocationEndTime) {
				
		//instancia o parser específico de cada versão
		CommandParser commandParser = buildCommandParser(new byte[0], version, 0);
		return commandParser.buildPriceOptions(timeOptions, previousAllocationEndTime);
	}
	
	public static byte[] buildAuthorizationResponseForAllocation(Integer eventId, Float prePaidCardBalance) {
		return buildAuthorizationResponse(eventId, prePaidCardBalance, null, null);
	}
	
	public static byte[] buildAuthorizationResponseRegularization(Integer eventId, byte typeReg, float regularizationValue, byte ruf) {
		return buildAuthorizationResponse(eventId, null, typeReg, regularizationValue);
	}	
	/**
	 * @param prePaidCardBalance, utilizado nas alocações, para enviar o saldo do usuário para o parquímetro imprimir no comprovante
	 * @param typeReg Tipo de regularização (0x60=NI carro, 0x61=NI moto, 0x63=TPU moto, 0x64=TPU carro). NI = Notificação, TPU = Tarifa pós uso 
	 */
	private static byte[] buildAuthorizationResponse(Integer eventId, Float prePaidCardBalance, Byte typeReg, Float regularizationValue) {
		
		byte[] messageHeaders = new byte[]{
				DigiconCommandParser.SD3, //SD3
				DigiconCommandParser.VERSAO_3, //VERSAO
				(byte) 0x06, //TAMANHO 6 bytes
				DigiconCommandParser.ACK, //ACK
		};
		
		byte[] messageEnd = new byte[] {
				(byte) 0x00, //CS checksum alimentado em seguida
				DigiconCommandParser.END //END
		};
		
		int balance = Utils.parseFloatToInt(prePaidCardBalance, 2);
		
		byte[] messageBody = new byte[6];
		messageBody[0] = (byte) ((eventId >> 16) & 0xFF);
		messageBody[1] = (byte) ((eventId >> 8) & 0xFF);
		messageBody[2] = (byte) ((eventId & 0xFF));
		
		if(prePaidCardBalance != null) {
			messageBody[3] = (byte) ((balance >> 16) & 0xFF);
			messageBody[4] = (byte) ((balance >> 8) & 0xFF);
			messageBody[5] = (byte) (balance & 0xFF);
		} else {
			if(typeReg != null) {
				messageBody[2] = typeReg;
			}
			
			if(regularizationValue != null) {
				int regularizationValueInt = Utils.parseFloatToInt(regularizationValue, 2);
				messageBody[3] = (byte) ((regularizationValueInt >> 8) & 0xFF);
				messageBody[4] = (byte) (regularizationValueInt & 0xFF);
			}
			
			//messageBody[5] = Reservado uso futuro
		}

		byte[] result = buidWriteMessage(messageHeaders, messageBody, messageEnd);
		
		return result;
	}
	
	public static byte[] buildLoadAllocation(int availableTime, List<AllocationTimeOptionVO> timeOptions) {

		//DAT0  - DAT1 = tempo disponível
		byte[] availableTimeBytes = new byte[2];
		availableTimeBytes[0] = (byte) ((availableTime >> 8) & 0xFF);
		availableTimeBytes[1] = (byte) (availableTime & 0xFF);
		
		//DAT2  - DAT42 = tarifas
		byte[] tarifas = new byte[timeOptions.size() * 2];
		
		//DAT43 - DAT82 = horario
		byte[] horarios = new byte[timeOptions.size() * 2];
		
		int pos = 0;
		for(AllocationTimeOptionVO t : timeOptions) {
			
			int value = Utils.parseFloatToInt(t.getTotalValue(),2);
			int time = t.getTotalMinCardTime();
			
			tarifas[pos] = (byte) ((value >> 8) & 0xFF);
			horarios[pos] = (byte) ((time >> 8) & 0xFF);
			pos++;

			tarifas[pos] = (byte) (value & 0xFF);
			horarios[pos] = (byte) (time & 0xFF);			
			pos++;
		}
		
		byte[] messageBody = new byte[availableTimeBytes.length + tarifas.length + horarios.length + 2];
		System.arraycopy(availableTimeBytes, 0, messageBody, 0, tarifas.length);
		System.arraycopy(tarifas, 0, messageBody, 0, availableTimeBytes.length);
		System.arraycopy(horarios, 0, messageBody, tarifas.length, horarios.length);
		
		byte[] messageHeaders = new byte[]{
				DigiconCommandParser.SD3, //SD3
				DigiconCommandParser.VERSAO_3, //VERSAO
				(byte) (messageBody.length & 0xFF), //TAMANHO 80 bytes
				DigiconCommandParser.ACK, //ACK
		};
		
		byte[] messageEnd = new byte[] {
				(byte) 0x00, //CS checksum alimentado em seguida
				DigiconCommandParser.END //END
		};

		byte[] result = buidWriteMessage(messageHeaders, messageBody, messageEnd);
		
		return result;
	}
	
	public static void writeCheckSum(byte[] result) {		
		int checkSumSize = (result.length - 2);
		byte[] checkSumArray = new byte[checkSumSize];
		System.arraycopy(result, 0, checkSumArray, 0, checkSumSize);
		result[checkSumSize] = Utils.checkSum(checkSumArray);
	}

	public static byte[] buildACK(short message, byte version){
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.putShort(message);
		byte[] ack = new byte[]{
				SD3, //SD3
				version, //VERSAO
				(byte) 0x02, //TAMANHO 2 bytes
				ACK, //ACK
				bb.get(0),   //DAT1 numero operacao
				bb.get(1),   //DAT2 numero operacao
				(byte) 0x00, //CS checksum alimentado em seguida
				END //END
				}; 
		
		byte[] checkSumArray = new byte[6];
		System.arraycopy(ack, 0, checkSumArray, 0, 6);
		ack[6] = Utils.checkSum(checkSumArray);
		
		return ack;
	}
	
	public static byte[] buildNAK(byte version) {
		return buildNAK(version, new byte[] {(byte)0x00, (byte)0x00});
	}
	
	public static byte[] buildNAK(byte version, byte[] error) {
		byte[] nak = new byte[]{
				SD3, //SD3
				version, //VERSAO
				(byte) 0x02, //TAMANHO 2 bytes
				NAK, //NAK
				(byte) 0x00, //DAT1 ERR - não utilizado
				(byte) 0x00, //DAT2 ERR - não utilizado
				(byte) 0x00, //DAT3 RUF - reservado uso futuro
				(byte) 0x00, //DAT4 RUF - reservado uso futuro
				(byte) 0x00, //DAT5 RUF - reservado uso futuro
				(byte) 0x00, //DAT6 RUF - reservado uso futuro
				(byte) 0x00, //CS checksum alimentado em seguida
				END //END
				}; 

		
		if(error != null) {
			System.arraycopy(error, 0, nak, 4, error.length);
		}
		
		byte[] checkSumArray = new byte[10];
		System.arraycopy(nak, 0, checkSumArray, 0, 10);
		nak[6] = Utils.checkSum(checkSumArray);
		
		return nak;
	}
	
	public static byte[] buildNAKWithErrorCode(byte version, byte[] error) {
		
		if(version == VERSAO_4) {		
			byte[] nak = new byte[]{
					SD3, //SD3
					version, //VERSAO
					(byte) 0x02, //TAMANHO 2 bytes
					NAK, //NAK
					(byte) 0x00, //DAT1 ERR - não utilizado
					(byte) 0x00, //DAT2 ERR - não utilizado
					(byte) 0x00, //CS checksum alimentado em seguida
					END //END
					}; 
	
			System.arraycopy(error, 0, nak, 4, 2);
			
			byte[] checkSumArray = new byte[6];
			System.arraycopy(nak, 0, checkSumArray, 0, 6);
			nak[6] = Utils.checkSum(checkSumArray);
			return nak;
		} else {

			return buildNAK(version, error);
		}
		
	}
	
	public static byte[] buildClock() {
		Calendar now = Calendar.getInstance();
		
		byte[] clock = new byte[]{
				DigiconCommandParser.SD3, //SD3
				DigiconCommandParser.VERSAO_1, //VERSAO
				(byte) 0x07, //TAMANHO 7 bytes
				DigiconCommandParser.ACK, //ACK
				parseByte( now.get(Calendar.DAY_OF_WEEK)  ), //DAT1 - dia da semana (1 = Domingo)
				parseByte( now.get(Calendar.DAY_OF_MONTH) ), //DAT2 - dia do mês
				parseByte( (now.get(Calendar.MONTH) + 1)  ), //DAT3 - mês do ano
				parseByte( (now.get(Calendar.YEAR) - 2000)), //DAT4 - ano
				parseByte( now.get(Calendar.HOUR_OF_DAY)  ), //DAT5 - hora
				parseByte( now.get(Calendar.MINUTE)       ), //DAT6	- minuto
				parseByte( now.get(Calendar.SECOND)       ), //DAT7 - segundo
				(byte) 0x00, //CS checksum alimentado em seguida
				DigiconCommandParser.END //END
				}; 
		
		byte[] checkSumArray = new byte[11];
		System.arraycopy(clock, 0, checkSumArray, 0, 11);
		clock[11] = Utils.checkSum(checkSumArray);
		
		return clock;
	}

	public static byte[] buildSondaAck(byte DAT1, byte DAT2, byte version) {
		byte[] ack = new byte[] {
				SD3, // SD3
				VERSAO_1, // VERSAO
				(byte) 0x02, // TAMANHO 2 bytes
				ACK, // ACK
				DAT1, // DAT1 ident_park - byte mais significativo
				DAT2, // DAT2 ident_park - byte menos significativo
				(byte) 0x00, // CS checksum alimentado em seguida
				END // END
		};

		byte[] checkSumArray = new byte[6];
		System.arraycopy(ack, 0, checkSumArray, 0, 6);
		ack[6] = Utils.checkSum(checkSumArray);

		return ack;
	}

	public static int parseInt(byte value) {
		ByteBuffer b = ByteBuffer.allocate(2);
		b.put((byte)0x00);
		b.put(value);
		return b.getShort(0);
	}
	
	public static byte parseByte(int i) {
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(i);
		return b.get(3);
	}
	
	private static JSONArray loadJSON(String file) throws JSONException {
		InputStream is = null;
        BufferedReader rd = null;
        try {
        	try {
        		is = new FileInputStream(file);
        	} catch (FileNotFoundException err) {
        		logger.warn("Arquivo " + file + " não existe na pasta atual, irá tentar utilizar o padrão.");
        		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        		is = classloader.getResourceAsStream(file);
        	}
            StringBuilder result = new StringBuilder();
            rd = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }

            JSONArray jsonArray = new JSONArray(result.toString());
            
            return jsonArray;

        } catch (IOException e) {
            logger.error("Erro ao ler o arquivo: "+ file, e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					logger.error("Erro ao fechar o inputStream.", e);
				}
			}
            if (rd != null) {
                try {
                    rd.close();
                } catch (IOException e) {
                    logger.error("Erro ao fechar o bufferReader", e);
                }
            }
        }
        
        return null;
	}
	
	public static Map<String, DigiconAllocationTypeVO> readDigiconAllocationTypeJSON(String file) {
		try{
	        Map<String, DigiconAllocationTypeVO> map = new HashMap<String, DigiconAllocationTypeVO>();
	        JSONArray jsonArray = loadJSON(file);
	
	        if (jsonArray != null && jsonArray.length() > 0) {
	        	for (int x = 0; x < jsonArray.length(); x++) {
	        		JSONObject jsonObject = jsonArray.getJSONObject(x);
	        		if (jsonObject.has("vehicleType")) {
	        			String vehicleType = jsonObject.getString("vehicleType");
	        			VehicleTypeEnum vehicleTypeEnum = VehicleTypeEnum.valueOf(vehicleType);
	        			
	        			if (jsonObject.has("listTimeOption")) {
	        				JSONArray jsonTimeOption = jsonObject.getJSONArray("listTimeOption");
	        				if (jsonTimeOption != null && jsonTimeOption.length() > 0 ) {
	        					for (int y = 0; y <jsonTimeOption.length(); y++) {
	        						JSONObject timeOptionObject = jsonTimeOption.getJSONObject(y);
	        						DigiconAllocationTypeVO vo = new DigiconAllocationTypeVO();
	        						
	        						Double value = timeOptionObject.getDouble("value");
	        						Integer timeType = timeOptionObject.getInt("timeType");
	        						Integer amount = timeOptionObject.getInt("amount");
	        						String area = timeOptionObject.has("vacancyAreaType") ? timeOptionObject.getString("vacancyAreaType") : null;
	        						
	        						vo.setValue(value);
	        						vo.setTimeType(timeType);
	        						vo.setAmount(amount);
	        						vo.setVehicleType(vehicleTypeEnum);
	        						vo.setVacancyAreaTypeEnum(area != null ? VacancyAreaTypeEnum.valueOf(area) : null);
	        						
	        						String key = vehicleTypeEnum.name() + "_" + (vo.getVacancyAreaTypeEnum() != null ? vo.getVacancyAreaTypeEnum().name() : null) + "_" + value; 
	        						map.put(key, vo);
	        					}
	        				}
	        			}
	        		}
	        	}
	        	
	        	return map;
	        }
	        
    	} catch (JSONException e) {
    		logger.error("Erro ao ler o arquivo: "+ file, e);
    	}
        
        return null;
	}
	
    public static Map<String, DigiconCommandVO> readDigiconCommandJSON(String file) {

    	try{
    		
	        Map<String, DigiconCommandVO> map = new HashMap<String, DigiconCommandVO>();
	        JSONArray jsonArray = loadJSON(file);
	
	        if (jsonArray != null && jsonArray.length() > 0) {
	        	for (int x = 0; x < jsonArray.length(); x++) {
	        		JSONObject jsonObject = jsonArray.getJSONObject(x);
	        		DigiconCommandVO vo = new DigiconCommandVO();
	        		parseDigiconCommandVO(jsonObject, vo);
	        		if (vo.getCode() != null) {
	        			map.put(vo.getCode().toString(), vo);
	        		}
	        	}
	        	
	        	return map;
	        }
	        
    	} catch (JSONException e) {
    		logger.error("Erro ao ler o arquivo: "+ file, e);
    	}
        
        return null;
    }
    
    private static void parseDigiconCommandVO(JSONObject jsonObject, DigiconCommandVO vo) throws JSONException {
    	if (jsonObject.has("code")) {
			vo.setCode(jsonObject.getInt("code"));
		} 
		
		if (jsonObject.has("description")) {
			vo.setDescription(jsonObject.getString("description"));
		}
		
		if (jsonObject.has("commandType")) {
			if (jsonObject.get("commandType") instanceof JSONArray) {
				JSONArray commandTypeArray = jsonObject.getJSONArray("commandType");
				if (commandTypeArray != null && commandTypeArray.length() > 0) {
					Map<String, DigiconCommandVO> commandTypeVariationMap = new HashMap<String, DigiconCommandVO>();
					vo.setCommandTypeVariationMap(commandTypeVariationMap);
					
					for(int index =0; index < commandTypeArray.length(); index++) {
						JSONObject commandTypeObject  = commandTypeArray.getJSONObject(index);
						DigiconCommandVO subVO = new DigiconCommandVO();
						parseDigiconCommandVO(commandTypeObject, subVO);
						if (subVO.getCode() != null) {
							commandTypeVariationMap.put(subVO.getCode().toString(), subVO);
						}
					}
				}
			} else {
				vo.setCommandType(CommandTypeEnum.valueOf(jsonObject.getString("commandType")));
			}
		}
		
		if(jsonObject.has("status")) {
			if(jsonObject.get("status") instanceof JSONArray) {
				JSONArray statusArray = jsonObject.getJSONArray("status");
				if(statusArray != null && statusArray.length() > 0) {
					Map<String, DigiconCommandVO> statusVariationMap = new HashMap<String, DigiconCommandVO>();
					vo.setStatusVariationMap(statusVariationMap);
					
					for(int index =0; index < statusArray.length(); index++) {
						JSONObject statusObject = statusArray.getJSONObject(index);
						DigiconCommandVO subVO = new DigiconCommandVO();
						parseDigiconCommandVO(statusObject, subVO);
						if(subVO.getCode() != null) {
							statusVariationMap.put(subVO.getCode().toString(), subVO);
						}
					}
				} 
			}else {
				vo.setStatus(SalePointStatusEnum.valueOf(jsonObject.getString("status")));
			}
		}
		
		if(jsonObject.has("statusType")) {
			if(jsonObject.get("statusType") instanceof JSONArray) {
				JSONArray statusTypeArray = jsonObject.getJSONArray("statusType");
				if(statusTypeArray != null && statusTypeArray.length() > 0) {
					Map<String, DigiconCommandVO> statusTypeVariationMap = new HashMap<String, DigiconCommandVO>();
					vo.setStatusTypeVariationMap(statusTypeVariationMap);
					
					for(int index=0; index < statusTypeArray.length(); index++) {
						JSONObject statusTypeObject = statusTypeArray.getJSONObject(index);
						DigiconCommandVO subVO = new DigiconCommandVO();
						parseDigiconCommandVO(statusTypeObject, subVO);
						if(subVO.getCode() != null) {
							statusTypeVariationMap.put(subVO.getCode().toString(), subVO);
						}
					}
				}
			}else {
				vo.setStatusType(ParkingMeterStatusEnum.valueOf(jsonObject.getString("statusType")));
			}
		}
		
		if (jsonObject.has("vehicleType")) {
			vo.setVehicleType(VehicleTypeEnum.valueOf(jsonObject.getString("vehicleType")));
		}
    }

	public static byte[] buidWriteMessage(byte[] messageHeaders, byte[] messageBody, byte[] messageEnd) {
		byte[] result = new byte[messageHeaders.length + messageBody.length + messageEnd.length];
		
		int pos = 0;
		System.arraycopy(messageHeaders, 0, result, pos, messageHeaders.length);
		
		pos += messageHeaders.length;
		System.arraycopy(messageBody, 0, result, pos, messageBody.length);
		
		pos += messageBody.length;
		System.arraycopy(messageEnd, 0, result, pos, messageEnd.length);
		
		writeCheckSum(result);
		
		return result;
	}
	
	private static void testCommand(byte[] commandBytes, byte version) {
				
		Socket clientSocket = new Socket();
		CommandThread commandThread = new CommandThread(clientSocket);
		try {
			commandThread.processMessage(commandBytes, new OutputStream() {
				
				@Override
				public void write(int b) throws IOException {
					System.out.println(b);
					
				}
			}, version);
		} catch (PartialMessageException e) {
			e.printStackTrace();
		} catch (MessageException e) {
			e.printStackTrace();
		} catch (ChecksumException e) {
			e.printStackTrace();
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
	
	private static void testCommand(String[] hexCommandBytes, byte version) {
		String[] byteArrayHex = hexCommandBytes;
		
		byte[] commandBytes = new byte[byteArrayHex.length];
		int pos = 0;
		for (String byteHex : byteArrayHex) {
		   int j = Integer.parseInt(byteHex, 16);
		   commandBytes[pos++] = (byte) j;
		}
		
		byte[] checkSumArray = new byte[hexCommandBytes.length-2];
		System.arraycopy(commandBytes, 0, checkSumArray, 0, hexCommandBytes.length-2);
		logger.debug("CheckSum: " + (int) Utils.checkSum(checkSumArray));
		int checksum = (int) Utils.checkSum(checkSumArray);
		
		commandBytes[commandBytes.length-2] = (byte) parseByte(checksum);
		
		testCommand(commandBytes, version);
	}
	
	private static void testLoadPriceOptionsv4() {
		testCommand(new String[] {"A2","04","0C","56","58","7F","46","47","4E","31","35","33","36","03","E8","0A","81","16"}, VERSAO_4);
	}
	
	private static void testConsultaTarifa() {
		testCommand(new String[] {"A2","01","0A","56","50","94","4D","4D","4D","19","FC","03","E8","0A","27","16"}, VERSAO_1);
	}
	
	private static void testEvent() {		
		testCommand(new String[] {"A2","01","1D","4C","36","9D","0A","8F","B3","11","0A","13","0A","1F","00","00","00","00","00","6E","10","00","00","00","4A","48","47","0D","61","00","00","04","8F","25","16"}, VERSAO_1);	
	}
	
	private static void testAuthorizationRegularization() {
		testCommand(new String[] {"A2","03","1F","4C","50","94","60","07","48","18","0A","13","10","2D","00","01","11","81","05","DC","2C","00","00","02","00","00","00","00","00","00","00","00","00","04","A8","9C","16"}, VERSAO_3);
	}
	
	private static void testLoadBalance() {
		testCommand(new String[] {"A2","03","0C","42","06","00","00","57","2A","00","00","00","6C","26","D4","1C","03","16"}, VERSAO_3);
	}

	private static void replacePlate(String[] event, String plate, int position) {
		byte[] plateB = new byte[plate.length()];
		int i = 0;
		for(char p : plate.toCharArray()) {
			byte b = (byte) p;
			plateB[i++] = b;
		}		
		i = position-1;
		for(String x : Utils.byte2HexStringPrint(plateB).split(Pattern.quote(" | "))) {
			event[i++] = x;
		}
	}

	private static void replaceValue(String[] event, float valueF, int position) {
		byte[] plateB = new byte[2];
		int i = 0;

		int value = (int) (valueF * 100f);
		plateB[0] = (byte) ((value >> 8) & 0xFF);
		plateB[1] = (byte) (value & 0xFF);
		
		i = position-1;
		for(String x : Utils.byte2HexStringPrint(plateB).split(Pattern.quote(" | "))) {
			event[i++] = x;
		}
	}

	private static void replaceDate(String[] event, String date, int position) {
		
		Calendar calendar = Calendar.getInstance();
		try {
			calendar.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(date));
		} catch (ParseException e) {
			e.printStackTrace();
		}		
		
		byte[] dateB = new byte[5];
		dateB[0] = parseByte(calendar.get(Calendar.DATE));
		dateB[1] = parseByte(calendar.get(Calendar.MONTH)+1);
		dateB[2] = parseByte(calendar.get(Calendar.YEAR)-2000);
		dateB[3] = parseByte(calendar.get(Calendar.HOUR_OF_DAY));
		dateB[4] = parseByte(calendar.get(Calendar.MINUTE));
		
		int i = position-1;
		for(String x : Utils.byte2HexStringPrint(dateB).split(Pattern.quote(" | "))) {
			System.out.println(x);
			event[i++] = x;
		}
	}
	
	private static void testCancelAuthorization(String plate, String date, float value) {
		String hexa = "A2 | 03 | 1F | 4C | 58 | 7F | 0A | 0F | 84 | 12 | 0C | 14 | 10 | 0A | 00 | 00 | 00 | 00 | 00 | 64 | FD | 00 | 00 | 00 | 45 | 46 | 47 | 38 | 38 | 39 | 36 | 00 | 00 | 04 | D2 | 47 | 16";
		String[] cancelEvent = hexa.split(" \\| ");

		replaceDate(cancelEvent, date, 10);
		replaceValue(cancelEvent, value, 19);
		replacePlate(cancelEvent, plate, 25);
//		replaceAllocationMinutes(cancelEvent, allocationInMinutes, 22);
		
		testCommand(cancelEvent, VERSAO_3);
	}
	
	private static void testAuthorization(String plate, String date, float value, int allocationMinutes) {		
		String hexa = "A2 | 03 | 1F | 41 | 58 | 7F | 0A | 0F | 83 | 12 | 0C | 14 | 10 | 0A | 00 | 00 | 00 | 00 | 00 | 64 | 10 | 00 | 00 | 3C | 45 | 46 | 47 | 38 | 38 | 39 | 36 | 00 | 00 | 00 | 00 | DA | 16";
		String[] authorizationEvent = hexa.split(" \\| ");
//		String[] authorizationEvent = new String[] {"A2","03","1F","41","58","7F","0A","0A","98","13","08","14","0F","09","00","01","66","F9","00","00","2D","00","00","00","46","4E","4D","31","32","33","34","03","E8","00","00","0D","16"};
		
		replaceDate(authorizationEvent, date, 10);
		replaceValue(authorizationEvent, value, 19);
		replacePlate(authorizationEvent, plate, 25);
		replaceAllocationMinutes(authorizationEvent, allocationMinutes, 22);

		for(String x : authorizationEvent) {
			System.out.print(x + " ");
		}
		
		testCommand(authorizationEvent, VERSAO_3);
	}

	private static void replaceAllocationMinutes(String[] event, int minutes, int position) {
		byte[] allocationMinutesB = new byte[3];
		int i = 0;

		allocationMinutesB[0] = (byte) ((minutes >> 16) & 0xFF);
		allocationMinutesB[1] = (byte) ((minutes >> 8) & 0xFF);
		allocationMinutesB[2] = (byte) (minutes & 0xFF);
		
		i = position-1;
		for(String x : Utils.byte2HexStringPrint(allocationMinutesB).split(Pattern.quote(" | "))) {
			event[i++] = x;
		}
	}
		
	private static void testUndoAuthorization(String plate, String date) {
		String[] undoEvent = new String[] {"A2","03","1F","4C","58","7F","0A","05","09","16","05","14","0E","25","00","00","00","00","00","C8","FD","00","00","00","4E","47","55","31","30","30","30","03","E8","04","AA","95","16"};
		
		replaceDate(undoEvent, date, 10);
		replacePlate(undoEvent, plate, 25);

		for(String x : undoEvent) {
			System.out.print(x + " ");
		}
		
		testCommand(undoEvent, VERSAO_3);
	}
	
	public static void main(String[] args) {
		/*Calendar now = Calendar.getInstance();
		
		CommandParkmeterVO vo = new CommandParkmeterVO();
		vo.setExternalCommand("194");
		vo.setExternalSubCommand("225");
	
		parseExternalCodes(vo, null);
		
		System.out.println(vo.getCommandType());
		System.out.println(vo.getParkMeterStatus());
		System.out.println(vo.getParkMeterStatusType());
		System.out.println(vo.getExternalCommandDescription());
		System.out.println(vo.getExternalSubCommandDescription());*/
		
//		testConsultaTarifa();
//		testEvent();
//		testAuthorizationRegularization();
//		testEvent();
//		testLoadPriceOptionsv4();
		
//		String[] byteArrayHex = new String[] {"14","05","0D","0D","23"};
//		
//		byte[] commandBytes = new byte[byteArrayHex.length];
//		int pos = 0;
//		for (String byteHex : byteArrayHex) {
//		   int j = Integer.parseInt(byteHex, 16);
//		   System.out.println(j);
//		}
		
		testAuthorization("CCC5554", "2021-03-31 15:15", 2f, 60);
		
		//testCancelAuthorization("CCC5555", "2020-12-24 00:16", 2f);
		
//		testLoadPriceOptionsv4();
//		testUndoAuthorization("BBB7777", "2020-12-23 16:29");
		
//		System.out.println(parseInt(parseByte( now.get(Calendar.DAY_OF_WEEK)  )));
//		System.out.println(parseInt(parseByte( now.get(Calendar.DAY_OF_MONTH) )));
//		System.out.println(parseInt(parseByte( (now.get(Calendar.MONTH) + 1)  )));
//		System.out.println(parseInt(parseByte( (now.get(Calendar.YEAR) - 2000))));
//		System.out.println(parseInt(parseByte( now.get(Calendar.HOUR_OF_DAY)  )));
//		System.out.println(parseInt(parseByte( now.get(Calendar.MINUTE)       )));
//		System.out.println(parseInt(parseByte( now.get(Calendar.SECOND)       )));
	}
}