package com.alpdex.sered.digicon.agent;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;

import com.alpdex.sered.allocation.vo.AllocationTimeOptionVO;
import com.alpdex.sered.digicon.agent.exception.ChecksumException;
import com.alpdex.sered.sales.parkingmeter.enumeration.CardBrandEnum;
import com.alpdex.sered.vehicle.enumeration.VehicleTypeEnum;

public class DigiconCommandParserV01 implements CommandParser {
	
	private static final Logger logger = Logger.getLogger(DigiconCommandParserV01.class);
	
	protected byte[] commandBytes;
	protected int currentPosition = 0;
	
	public DigiconCommandParserV01(byte[] commandBytes) {
		this.commandBytes = commandBytes;
	}
	
	@Override
	public int getCurrentPosition() {
		return this.currentPosition;
	}	
	
	@Override
	public void setCurrentPosition(int position) {
		this.currentPosition = position;
	}
	
	@Override
	public int parseParkmeterCode() {
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte menos significativo
		byte DAT2 = commandBytes[this.currentPosition++];
		int identPark = ((DAT1 & 0xff) << 8) | (DAT2 & 0xff);
		logger.debug("Ident_park:" + identPark);
		return identPark;
	}
	
	@Override
	public int parseExternalCommand() {
		Byte DAT3 = commandBytes[this.currentPosition++];
		int operacao = parseInt(DAT3);
		logger.debug("Cod_operacao:" + operacao);
		return operacao;
	}
	
	@Override
	public int parseSequence() {
		ByteBuffer bb = ByteBuffer.allocate(2);
		// DAT4 número da operação – byte mais significativo
		byte DAT4 = commandBytes[this.currentPosition++];
		bb.put(DAT4);
		// DAT5 número da operação – byte menos significativo
		byte DAT5 = commandBytes[this.currentPosition++];
		bb.put(DAT5);
		short numOperacao = bb.getShort(0);
		int sequence = (int)numOperacao;
		logger.debug("Número do evento: " + sequence);
		return sequence;
	}
	
	@Override
	public Calendar parseEventDate() {
		Calendar eventDate = Calendar.getInstance();
		eventDate.set(Calendar.SECOND, 0);
		eventDate.set(Calendar.MILLISECOND, 0);
		
		// DAT6 dia
		byte DAT6 = commandBytes[this.currentPosition++];
		int day = parseInt(DAT6);
		eventDate.set(Calendar.DATE, day);
		logger.debug("Dia: " + day);

		// DAT7 mês
		byte DAT7 = commandBytes[this.currentPosition++];
		int month = parseInt(DAT7);
		eventDate.set(Calendar.MONTH, month -1);
		logger.debug("Mes: " + month);

		// DAT8 ano
		byte DAT8 = commandBytes[this.currentPosition++];
		int year = parseInt(DAT8);
		eventDate.set(Calendar.YEAR, 2000 + year);
		logger.debug("Ano: " + year);

		// DAT9 hora
		byte DAT9 = commandBytes[this.currentPosition++];
		int hour = parseInt(DAT9);
		eventDate.set(Calendar.HOUR_OF_DAY, hour);
		logger.debug("Hora: " + hour);

		// DAT10 minuto
		byte DAT10 = commandBytes[this.currentPosition++];
		int minute = parseInt(DAT10);
		eventDate.set(Calendar.MINUTE, minute);
		logger.debug("Minuto: " + minute);
		logger.debug("Date: " + eventDate.getTime().toString());
		
		return eventDate;
	}
	
	@Override
	public String parsePrePaidCardCode() {
		StringBuilder card = new StringBuilder();
		// DAT11 número serial do cartão ou ID usuário – byte (+) significativo
		byte DAT11 = commandBytes[this.currentPosition++];
		int i = DAT11 & 0xff;
		card.append(String.format("%03d", i));
		// DAT12 “”
		byte DAT12 = commandBytes[this.currentPosition++];
		i = DAT12 & 0xff;
		card.append(String.format("%03d", i));
		// DAT13 ””
		byte DAT13 = commandBytes[this.currentPosition++];
		i = DAT13 & 0xff;
		card.append(String.format("%03d", i));
		// DAT14 número serial do cartão ou ID usuário – byte (-) significativo
		byte DAT14 = commandBytes[this.currentPosition++];
		i = DAT14 & 0xff;
		card.append(String.format("%03d", i));
		String prePaidCardCode = (card.length() > 0 && !card.toString().equals("000000000000")) ? card.toString() : null;
		logger.debug("User ID: " + prePaidCardCode);
		return prePaidCardCode;
	}
	
	public String parsePrePaidCardCodeMifarePlus() {
		this.currentPosition++;
		this.currentPosition++;
		this.currentPosition++;
		return parsePrePaidCardCode();
	}
	
	@Override
	public float parseValue() {
		ByteBuffer bb = ByteBuffer.allocate(2);
		// DAT15 valor da operação – byte (+) significativo
		byte DAT15 = commandBytes[this.currentPosition++];
		bb.put(DAT15);
		// DAT16 valor da operação – byte (-) significativo
		byte DAT16 = commandBytes[this.currentPosition++];
		bb.put(DAT16);
		int valorOperacao = bb.getShort(0);
		logger.debug("Valor operacao: " + valorOperacao);
		return Utils.parseValue(valorOperacao);
	}
	
	@Override
	public String parseExternalSubCommand() {
		// DAT17 código da suboperação
		byte DAT17 = commandBytes[this.currentPosition++];
		int subOperacao = parseInt(DAT17);
		String subOperacaoStr = Integer.toString(subOperacao);
		logger.debug("Cod da SubOperacao: " + subOperacaoStr);
		return subOperacaoStr;
	}
	
	@Override
	public float parsePrePaidCardBalance() {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.put((byte)0x00);
		// DAT18 saldo do cartão – byte (+) significativo
		byte DAT18 = commandBytes[this.currentPosition++];
		bb.put(DAT18);
		// DAT19 “”
		byte DAT19 = commandBytes[this.currentPosition++];
		bb.put(DAT19);
		// DAT20 saldo do cartão – byte (-) significativo
		byte DAT20 = commandBytes[this.currentPosition++];
		bb.put(DAT20);
		int saldoCartao = bb.getInt(0);
		logger.debug("Saldo do Cartão: " + saldoCartao);
		return Utils.parseValue(saldoCartao);		
	}
	
	public String parsePlate() {
		
		// DAT21 1ª letra placa veículo
		byte DAT21 = commandBytes[this.currentPosition++];
		Character letra1 = DAT21 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT21);
		// DAT22 2ª letra placa veículo
		byte DAT22 = commandBytes[this.currentPosition++];
		Character letra2 = DAT22 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT22);
		// DAT23 3ª letra placa veículo
		byte DAT23 = commandBytes[this.currentPosition++];
		Character letra3 = DAT23 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT23);

		ByteBuffer bb = ByteBuffer.allocate(2);
		// DAT24 placa numérica veículo (+) significativo
		byte DAT24 = commandBytes[this.currentPosition++];
		bb.put(DAT24);
		// DAT25 placa numérica veículo (-) significativo
		byte DAT25 = commandBytes[this.currentPosition++];
		bb.put(DAT25);
		int numeroPlaca = bb.getShort(0);
		StringBuilder sb = new StringBuilder();
		if (letra1 != null) sb.append(letra1);
		if (letra2 != null) sb.append(letra2);
		if (letra3 != null) sb.append(letra3);
		if (sb.length() > 1) {
			sb.append(StringUtils.leftPad(Integer.toString(numeroPlaca), 4, '0'));
		}
			
		String plate = sb.toString();
		logger.debug("Placa: " + plate);
		return plate;
	}
	
	@Override
	public short parseVacancyNumber() {
		ByteBuffer bb = ByteBuffer.allocate(2);
		// DAT26 vaga (+) significativo
		byte DAT26 = commandBytes[this.currentPosition++];
		bb.put(DAT26);
		// DAT27 vaga (-) significativo
		byte DAT27 = commandBytes[this.currentPosition++];
		bb.put(DAT27);
		short vaga = bb.getShort(0);
		logger.debug("Vaga: " + vaga);
		return vaga;
	}
	
	@Override
	public VehicleTypeEnum parseVehicleType() {

		VehicleTypeEnum vehicleTypeEnum = null;
		byte DAT10 = commandBytes[this.currentPosition++];
		if(DAT10 == DigiconCommandParser.CONST_VEHICLE_TYPE_CAR) {
			vehicleTypeEnum = VehicleTypeEnum.CAR;
		} else if(DAT10 == DigiconCommandParser.CONST_VEHICLE_TYPE_MOTORCYCLE) {
			vehicleTypeEnum = VehicleTypeEnum.MOTORCYCLE;
		}
		return vehicleTypeEnum;
	}
	
	@Override
	public short parseBatteryStatus() {
		ByteBuffer bb = ByteBuffer.allocate(2);
		// DAT28 tensão da bateria – byte (+) significativo
		byte DAT28 = commandBytes[this.currentPosition++];
		bb.put(DAT28);
		// DAT29 tensão da bateria – byte (-) significativo
		byte DAT29 = commandBytes[this.currentPosition++];
		bb.put(DAT29);
		short tensaoBateria = bb.getShort(0);
		logger.info("Tensão bateria: " + tensaoBateria);
		return tensaoBateria;
	}

	@Override
	public int checksum() throws ChecksumException {
		// CS inverte resultado(SD3+VERSAO+TAM+“L”+DAT1+ ...+DAT29)

		int size = currentPosition;
		byte b = commandBytes[currentPosition++];
		
		byte[] checkSumArray = new byte[size];
		System.arraycopy(commandBytes, 0, checkSumArray, 0, size);
		logger.debug("CheckSum: " + (int) b + " = " + (int) Utils.checkSum(checkSumArray));
		int checksum = (int) Utils.checkSum(checkSumArray);
		if ((int) b != checksum) {
			throw new ChecksumException();
		}
		return checksum;
	}
	
	@Override
	public int parseAllocationTimeInMinutes() {
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte mais significativo
		byte DAT2 = commandBytes[this.currentPosition++];
		// DAT3 ident_park - byte menos significativo
		byte DAT3 = commandBytes[this.currentPosition++];
		int time = ((DAT1 & 0xff) << 16) | ((DAT2 & 0xff) << 8) | (DAT3 & 0xff);
		logger.debug("Tempo adquirido: " + time);
		return time;
	}
	
	@Override
	public String parseRegularizationID() {
		return null; //VERSÃO 1 não utiliza
	}
	
	@Override
	public String parseFederalIdentification() {
		
		int length = 14; //Qtd máxima de caracteres de um CNPJ;	
		
		String federalIdentification = getStringFromBytes(commandBytes, currentPosition, length);
		currentPosition += length;
		federalIdentification = federalIdentification.replaceAll("000000$", "");
		return federalIdentification;
	}
	
	@Override
	public String parseUserPassword(int length) {		
		String userPassword = getStringFromBytes(commandBytes, currentPosition, length);
		currentPosition += length;
		return userPassword;		
	}
	
	@Override
	public String parsePrePaidCardCodeAsNotificationNumber() {
		return null; //V1 não utiliza
	}
	
	@Override
	public Integer parseLoadBalanceType() {
		return null; //V1 não utiliza
	};
	
	@Override
	public void clockRUF() {
		this.currentPosition++; //uso futuro
		this.currentPosition++; //uso futuro
	}
	
	protected static String getStringFromBytes(byte[] bytes, int ini, int size) {	
		byte[] textBytes = new byte[size];
		System.arraycopy(bytes, ini, textBytes, 0, size);
		return new String(textBytes);
	}
	
	protected static int parseInt(byte value) {
		ByteBuffer b = ByteBuffer.allocate(2);
		b.put((byte)0x00);
		b.put(value);
		return b.getShort(0);
	}
	
	@Override
	public Integer parseUserId() {
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte mais significativo
		byte DAT2 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte mais significativo
		byte DAT3 = commandBytes[this.currentPosition++];
		// DAT3 ident_park - byte menos significativo
		byte DAT4 = commandBytes[this.currentPosition++];
		int userId = ((DAT1 & 0xff) << 24) | ((DAT2 & 0xff) << 16) | ((DAT3 & 0xff) << 8) | (DAT4 & 0xff);
		logger.debug("User ID: " + userId);
		return userId;
	}
	
	@Override
	public byte[] buildPriceOptions(List<AllocationTimeOptionVO> timeOptions, Calendar previousAllocationEndTime) {
		
		//DAT1  - DAT40 = tarifas
		byte[] tarifas = new byte[40];
		
		//DAT41 - DAT80 = horario
		byte[] horarios = new byte[40];	
		
		byte[] messageBody = new byte[tarifas.length + horarios.length];
		
		byte[] messageHeaders = new byte[]{
				DigiconCommandParser.SD3, //SD3
				DigiconCommandParser.VERSAO_1,
				DigiconCommandParser.parseByte(messageBody.length), //TAMANHO 80 bytes
				DigiconCommandParser.ACK, //ACK
		};
		
		byte[] messageEnd = new byte[] {
				(byte) 0x00, //CS checksum alimentado em seguida
				DigiconCommandParser.END //END
		};
		
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
		
		System.arraycopy(tarifas, 0, messageBody, 0, tarifas.length);
		System.arraycopy(horarios, 0, messageBody, tarifas.length, horarios.length);

		byte[] result = DigiconCommandParser.buidWriteMessage(messageHeaders, messageBody, messageEnd);
		return result;
	}

	@Override
	public String parsePaymentAuthenticator() {
		StringBuilder stringBuilder = new StringBuilder();
		// DAT11 NSU Autenticador
		byte DAT11 = this.commandBytes[this.currentPosition++];
		int i = DAT11 & 0xff;
		stringBuilder.append(String.format("%03d", i));
		// DAT12 “”
		byte DAT12 = this.commandBytes[this.currentPosition++];
		i = DAT12 & 0xff;
		stringBuilder.append(String.format("%03d", i));
		// DAT13 ””
		byte DAT13 = this.commandBytes[this.currentPosition++];
		i = DAT13 & 0xff;
		stringBuilder.append(String.format("%03d", i));
		// DAT14
		byte DAT14 = this.commandBytes[this.currentPosition++];
		i = DAT14 & 0xff;
		stringBuilder.append(String.format("%03d", i));
		String paymentAuthenticator = (stringBuilder.length() > 0 && !stringBuilder.toString().equals("000000000000")) ? stringBuilder.toString() : null;
		logger.debug("PaymentAuthenticator: " + paymentAuthenticator);
		return paymentAuthenticator;
	}

	@Override
	public CardBrandEnum parseCardBrand() {
		CardBrandEnum cardBrandEnum = null;

		byte DAT20 = this.commandBytes[this.currentPosition++];
		int i = DAT20 & 0xff;
		String cardBrand = String.format("%03d", i);
		if (StringUtils.isNotBlank(cardBrand) && NumberUtils.isNumber(cardBrand)) {
			cardBrandEnum = CardBrandEnum.getByValue(Integer.valueOf(cardBrand));
		}
		return cardBrandEnum;
	}
}