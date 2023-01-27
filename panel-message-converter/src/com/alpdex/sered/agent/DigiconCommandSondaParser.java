package com.alpdex.sered.digicon.agent;

import java.nio.ByteBuffer;
import java.util.Date;

import org.apache.log4j.Logger;

import com.alpdex.sered.digicon.agent.exception.ChecksumException;
import com.alpdex.sered.sales.parkingmeter.enumeration.CommandTypeEnum;
import com.alpdex.sered.sales.parkingmeter.vo.CommandParkmeterVO;

public class DigiconCommandSondaParser {

	private static final Logger logger = Logger.getLogger(DigiconCommandSondaParser.class);

	public void parse(byte[] commandBytes, CommandParkmeterVO vo) throws ChecksumException {
		logger.info("Sonda Command");

		vo.setCommandType(CommandTypeEnum.SONDA);
		vo.setEventDate(new Date());

		byte DAT1 = commandBytes[4]; // 00H CPU ANTIGA ou 01H CPU NOVA
		byte DAT2 = commandBytes[5]; // DAT2 ident_park - byte mais significativo
		byte DAT3 = commandBytes[6]; // DAT3 ident_park - byte menos significativo
		byte DAT4 = commandBytes[7]; // versão firmware letra (ASCII)
		byte DAT5 = commandBytes[8]; // versão firmware número (ASCII)
		byte DAT6 = commandBytes[9]; // 00H CPU ANTIGA ou número (ASCII) CPU NOVA
		byte DAT7 = commandBytes[10]; // configuração Estrutura de bits (BIT 0 ao BIT7)
		byte DAT8 = commandBytes[11]; // configuração Estrutura de bits (BIT 8 ao BIT15)
		byte DAT9 = commandBytes[12]; // configuração Estrutura de bits (BIT 16 ao BIT23)
		byte DAT10 = commandBytes[13]; // configuração Estrutura de bits (BIT 24 ao BIT31)
		byte DAT11 = commandBytes[14]; // Status de erros, 0 = sem erros
		byte DAT12 = commandBytes[15]; // tensão da bateria – byte (+) significativo
		byte DAT13 = commandBytes[16]; // tensão da bateria – byte (-) significativo

		// codigo do parquimetro
		int identPark = Utils.getIdentPark(DAT2, DAT3);
		logger.debug("Ident_park:" + identPark);
		vo.setParkmeterCode(Integer.toString(identPark));

		// tensao da bateria
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.put(DAT12);
		byteBuffer.put(DAT13);
		vo.setBatteryStatus(Integer.valueOf(byteBuffer.getShort(0)));

		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append(DAT1 == 0 ? "CPU Antiga" : "CPU Nova");
		stringBuffer.append(" - Versão firmware: " + (char) DigiconCommandParser.parseInt(DAT4) + " " + (char) DigiconCommandParser.parseInt(DAT5));
		stringBuffer.append(" - Número CPU: " + (char) DigiconCommandParser.parseInt(DAT6) + " ");

		stringBuffer.append(this.getFormatedConfig(DAT7) + " ");
		stringBuffer.append(this.getFormatedConfig(DAT8) + " ");
		stringBuffer.append(this.getFormatedConfig(DAT9) + " ");
		stringBuffer.append(this.getFormatedConfig(DAT10) + " ");

		if (DAT11 != 0) {
			stringBuffer.append("Erros: " + (char) DigiconCommandParser.parseInt(DAT11));
		} else {
			stringBuffer.append("Sem Erros");
		}

		vo.setExternalCommand(CommandTypeEnum.SONDA.name());
		vo.setExternalCommandDescription(stringBuffer.toString());
	}

	private String getFormatedConfig(byte information) {
		StringBuffer stringBuffer = new StringBuffer();

		stringBuffer.append("[");
		String config = String.format("%8s", Integer.toBinaryString(information & 0xFF)).replace(' ', '0').replace("", ",");
		stringBuffer.append(config.substring(1, config.length() - 1));
		stringBuffer.append("]");

		return stringBuffer.toString();
	}
}