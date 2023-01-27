package com.alpdex.sered.digicon.agent;

import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;

import com.alpdex.sered.allocation.vo.AllocationTimeOptionVO;

public class DigiconCommandParserV04 extends DigiconCommandParserV03 {
	
	private static final Logger logger = Logger.getLogger(DigiconCommandParserV04.class);
	
	public DigiconCommandParserV04(byte[] commandBytes) {
		super(commandBytes);
	}
	
	@Override
	public int parseExternalCommand() {
		// DAT1 ident_park - byte mais significativo
		byte DAT1 = commandBytes[this.currentPosition++];
		// DAT2 ident_park - byte menos significativo
		byte DAT2 = commandBytes[this.currentPosition++];
		int operacao = ((DAT1 & 0xff) << 8) | (DAT2 & 0xff);
		logger.debug("Cod_operacao:" + operacao);
		return operacao;
	}
	
	@Override
	public byte[] buildPriceOptions(List<AllocationTimeOptionVO> timeOptions, Calendar previousAllocationEndTime) {
		
		//DAT1  - DAT40 = tarifas
		byte[] tarifas = new byte[40];
		
		//DAT41 - DAT80 = horario
		byte[] horarios = new byte[40];	
		
		//DAT81 - DAT85 = validate da alocação anterior (para emenda de horário)
		byte[] previous = new byte[5];
		
		byte[] messageBody = new byte[tarifas.length + horarios.length + previous.length];
		
		byte[] messageHeaders = new byte[]{
				DigiconCommandParser.SD3, //SD3
				DigiconCommandParser.VERSAO_4,
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
		if(previousAllocationEndTime != null) {
			
			int fullYear = previousAllocationEndTime.get(Calendar.YEAR);
			int year = fullYear % 100;  
			
			previous[0] = DigiconCommandParser.parseByte(year);
			previous[1] = DigiconCommandParser.parseByte(previousAllocationEndTime.get(Calendar.MONTH)+1);
			previous[2] = DigiconCommandParser.parseByte(previousAllocationEndTime.get(Calendar.DAY_OF_MONTH));
			previous[3] = DigiconCommandParser.parseByte(previousAllocationEndTime.get(Calendar.HOUR_OF_DAY));
			previous[4] = DigiconCommandParser.parseByte(previousAllocationEndTime.get(Calendar.MINUTE));
		}
		
		System.arraycopy(tarifas, 0, messageBody, 0, tarifas.length);
		System.arraycopy(horarios, 0, messageBody, tarifas.length, horarios.length);
		
		if(previousAllocationEndTime != null) {
			System.arraycopy(previous, 0, messageBody, (tarifas.length + horarios.length), previous.length);
		}

		byte[] result = DigiconCommandParser.buidWriteMessage(messageHeaders, messageBody, messageEnd);
		return result;
	}
}
