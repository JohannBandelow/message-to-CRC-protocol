package com.alpdex.sered.digicon.agent;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

public class DigiconCommandParserV03 extends DigiconCommandParserV01 {
	
	private static final Logger logger = Logger.getLogger(DigiconCommandParserV03.class);
	
	public DigiconCommandParserV03(byte[] commandBytes) {
		super(commandBytes);
	}
	
	/*@Override
	public int parseExternalCommand() {
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte menos significativo
		byte DAT2 = commandBytes[this.currentPosition++];
		int operacao = ((DAT1 & 0xff) << 8) | (DAT2 & 0xff);
		logger.debug("Cod_operacao:" + operacao);
		return operacao;
	}*/
	
	@Override
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
		// DAT24 4ª letra placa veículo
		byte DAT24 = commandBytes[this.currentPosition++];
		Character letra4 = DAT24 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT24);
		// DAT25 5ª letra placa veículo
		byte DAT25 = commandBytes[this.currentPosition++];
		Character letra5 = DAT25 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT25);
		// DAT26 6ª letra placa veículo
		byte DAT26 = commandBytes[this.currentPosition++];
		Character letra6 = DAT26 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT26);
		// DAT27 7ª letra placa veículo
		byte DAT27 = commandBytes[this.currentPosition++];
		Character letra7 = DAT27 == 0 ? null : (char) DigiconCommandParser.parseInt(DAT27);
		
		StringBuilder sb = new StringBuilder();
		if (letra1 != null) sb.append(letra1);
		if (letra2 != null) sb.append(letra2);
		if (letra3 != null) sb.append(letra3);
		if (letra4 != null) sb.append(letra4);
		if (letra5 != null) sb.append(letra5);
		if (letra6 != null) sb.append(letra6);
		if (letra7 != null) sb.append(letra7);
			
		String plate = sb.toString();
		logger.debug("Placa: " + plate);
		return plate;
	}
	
	@Override
	public String parseRegularizationID() {
		
		setCurrentPosition(currentPosition + 3); //Pula 3, na regularização não utiliza esses bytes.
		
		StringBuilder s = new StringBuilder();
		
		byte[] alfaNumericosBytes = new byte[3];
		alfaNumericosBytes[0] = commandBytes[this.currentPosition++];
		alfaNumericosBytes[1] = commandBytes[this.currentPosition++];
		alfaNumericosBytes[2] = commandBytes[this.currentPosition++];
		String alfaNumericos = new String(alfaNumericosBytes);
		s.append(!alfaNumericos.equals("000") ? alfaNumericos : "");
		
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.put((byte) 0x00);
		byte ID4 = commandBytes[this.currentPosition++];
		bb.put(ID4);
		byte ID5 = commandBytes[this.currentPosition++];
		bb.put(ID5);
		byte ID6 = commandBytes[this.currentPosition++];
		bb.put(ID6);
		int idNumber = bb.getInt(0);
		s.append(String.valueOf(idNumber));
		
		String idRegularizacao = s.toString();
		logger.info("Regularization ID: " + idRegularizacao);
		
		return idRegularizacao;
	}
	
	@Override
	public String parsePrePaidCardCodeAsNotificationNumber() {

		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.put(commandBytes[currentPosition++]);
		bb.put(commandBytes[currentPosition++]);
		bb.put(commandBytes[currentPosition++]);
		bb.put(commandBytes[currentPosition++]);		
		String prePaidCardCode = String.valueOf(bb.getInt(0));
		logger.debug("Regularization ID: " + prePaidCardCode);
		return prePaidCardCode;
	}
	
	@Override
	public Integer parseLoadBalanceType() {
		Byte DAT1 = commandBytes[this.currentPosition++];
		int loadBalanceType = parseInt(DAT1);
		logger.debug("Tipo de identificação:" + loadBalanceType);
		return loadBalanceType;
	}
}
