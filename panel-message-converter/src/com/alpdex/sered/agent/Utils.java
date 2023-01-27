package com.alpdex.sered.digicon.agent;

import java.math.BigDecimal;

import com.alpdex.AlpdexConstants;

public class Utils {
	public static final byte checkSum(byte[] byteArray) {
		byte checksum = 0;
		for (int i = 0; i < byteArray.length; i++) {
			checksum += byteArray[i];
		}
		return (byte) (checksum ^ 0xFF);
	}

	public static String byteStringPrint(byte[] bytes) {
		String ret = "";
		if (bytes != null) {
			for (Byte b : bytes) {
				ret += b + " | ";
			}
		}
		return ret;
	}

	public static String byte2HexStringPrint(byte[] bytes) {
		String ret = "";
		if (bytes != null) {
			for (Byte b : bytes) {
				ret += String.format("%02X", b.intValue() & 0xFF) + " | ";
			}
		}
		return ret;
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
	
	public static int parseValue(float value) {
		return (int)(value * 100f);
	}
	
	public static float parseValue(int value) {
		return (float)value / 100f;
	}
	
	/**
	 * Valida se uma placa possui o formato válido. Retorna true em caso positivo.
	 * Formato: XXX9999
	 * @param paneNumber
	 * @return
	 */
	public static Boolean isValidPaneFormat(String paneNumber, Boolean isPlateFreeFormat) {
		if (paneNumber == null) {
			return false;
		}
		
		paneNumber = paneNumber.replaceAll("[^A-Za-z0-9]",""); //limpa o que não for letra e número
		
		if (isPlateFreeFormat == null || !isPlateFreeFormat) {
			if (paneNumber.matches(AlpdexConstants.PLATE_FORMAT_PATTERN)) {
				return true;
			}
		} else if (paneNumber.matches(AlpdexConstants.PLATE_FREE_FORMAT_PATTERN)) {
			return true;
		}
		
		return false;
	}

	/**
	 * retorna o codigo de identificacao do parquimetro
	 * 
	 * @param dat1
	 * @param dat2
	 * @return
	 */
	public static int getIdentPark(byte dat1, byte dat2) {
		int identPark = ((dat1 & 0xff) << 8) | (dat2 & 0xff);
		return identPark;
	}

	public static int parseFloatToInt(Float value, int scale) {
		return new BigDecimal(value).setScale(scale, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100.0)).intValue();
	}
	
	static byte[] parseHexToBytes(String bytes) {
		String[] byteArrayHex = bytes.split("\\|");
		
		byte[] commandBytes = new byte[byteArrayHex.length];
		int pos = 0;
		for(String byteHex : byteArrayHex) {			  
			int j = Integer.parseInt(byteHex.trim(), 16);
			commandBytes[pos++] = (byte) j;			
		}
		return commandBytes;
	}
	
}
