package com.alpdex.sered;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpiderCRCCommandParser {

    private static final int MESSAGE_SIZE = 26;
    private static final String HEX_SEPARATOR = " | ";

    private static final String SOH = "01";
    private static final String STX = "02";
    private static final String ORIGIN_CLASS = "50";
    private static final byte ORIGIN_GROUP = 0x01;
    private static final byte ORIGIN_ID = 0x01;
    private static final String DESTINY_CLASS = "DB";
    private static final byte DESTINY_GROUP = 0x01;
    private static final byte DESTINY_ID = 0x01; //Pode ser que esse aqui represente o módulo de LED
    private static final String INPUT_COMMAND = "80";
    private static final byte FRAME_NUMBER = 0x01;
    private static final byte EXISTING_FRAMES_AMOUNT = 0x01;
    private static final String ETX = "03";

    private void parseComandToSpiderBytes(SpiderCommandVO command) {

    }

    public static void main(String[] args) throws IOException {
        SpiderCRCCommandParser crc = new SpiderCRCCommandParser();
        Socket socket = new Socket("localhost", 6969);
        OutputStream out = socket.getOutputStream();

        System.out.println("Escrevendo no servidor");
        String command = getCommandHeaderString();
        byte[] byteArr = crc.parseHexToInt(command);

        out.write(byteArr);

    }

    public static String getCommandHeaderString() {
        StringBuilder sb = new StringBuilder();
        sb
                .append(SOH).append(HEX_SEPARATOR)
                .append(STX).append(HEX_SEPARATOR)
                .append(ORIGIN_CLASS).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append(DESTINY_CLASS).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append(INPUT_COMMAND).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append(SOH).append(HEX_SEPARATOR)
                .append("00 | 02 | 01 | 01").append(HEX_SEPARATOR)
                .append(ETX);

        return sb.toString();
    }

    private byte[] parseHexToInt(String bytes) {
        System.out.println(bytes);
        String[] byteArrayHex = bytes.split("\\|");

        byte[] commandInt = new byte[byteArrayHex.length];
        int pos = 0;
        for(String byteHex : byteArrayHex) {
            int j = Integer.parseInt(byteHex.trim(), 16);

            commandInt[pos++] = (byte) j;
        }
        System.out.println(Arrays.toString(commandInt));
        return commandInt;
    }
}
