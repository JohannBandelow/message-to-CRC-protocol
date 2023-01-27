package com.alpdex.sered.digicon.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class AppClient {
	
	public static void main(String[] args) {
		//String s = "a2011d4c39060d00a80403101022000000000032100000004d4f54000b000004db9a16";
		//String s = "A24939062A16a2011d4c39060d00a80403101022000000000032100000004d4f54000b000004db9a16";
		//String s = "A2011D4C39060A00D30F0310122E12F353A90064200074044343430D05000004DABF16";
		//String s = "A2011D4C3906C001FE0101000A00000000000000EE0000000000000000000004D32416";
		//String s = "A201045239060000C716";
		//String s = "A2011D4C39060A026E0F04100F380000000000641000000051515104BE000004CED516";
//		String s = "A2011D4C3906C0020A0101000A00000000000000EE0000000000000000000004D51516";
//		String s = "FEC4000486CC16A24957C90B16A2010D530157C9433234001AFEC4000485CD16A24957C90B";
		String s = "0102500101DB01018001010008313131313131313103512F";
		
		//String s = "A201045239060000C716";
		// A2011D4C39060A026E0F04100F380000000000641000000051515104BE000004CED516
		byte[] byteArray = Utils.hexStringToByteArray(s);
		
		Socket socket = null;
        //String host = "177.71.248.8";
		String host = "192.168.1.2";

        InputStream in = null; 
        OutputStream out = null;
        
        try {
        	socket = new Socket(host, 2101);
        	out = socket.getOutputStream();
        	in = socket.getInputStream();
        	
        	System.out.println("Escrevendo no servidor");
        	
        	for (int x=0; x<byteArray.length;x++) {
        		out.write(byteArray[x]);
        		
        		//out.write(byteArray);
        	}
	        
        	System.out.println("Escrita terminada. Lendo do servidor a resposta.");
        	
        	byte[] responseMessage = new byte[37]; //max size
            boolean end = false;
            int pos = 0;
            while (!end) {
            	int readInt = in.read();
            	if (readInt == -1) {
            		end = true;
            	} else {
            		System.out.println(String.format("Byte recebido: %02X", readInt & 0xFF));
            		responseMessage[pos] = (byte) readInt;
            		if (responseMessage[pos] == DigiconCommandParser.END) {
            			end = true;
            		}
            		pos++;
            	}
            }
            
            System.out.println(Utils.byte2HexStringPrint(responseMessage));
            
        } catch (IOException e) {
			e.printStackTrace();
		} finally {
			try{
				if (in != null) {
					in.close();
				}
				
				if (out != null) {
					out.close();
				}
			
				if (socket != null) {
					socket.close();
				}
			}catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
