package com.alpdex.sered.digicon.agent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

public class AppServer {
	private static final Logger logger = Logger.getLogger(AppServer.class);
	
	/**
	 * Porta padrão de comunicação
	 */
	public static final int PORT;
	
	/**
	 * Padrão 30 segundos para chegar
	 */
	public static final int TIME_OUT_TO_CHECK_IDLE;
	
	/**
	 * Tempo para desconexão automática após inatividade.
	 */
	public static final int TIME_OUT_IDLE;
	
	/**
	 * Endereço JNDI para conexão
	 */
	public static final String SERED_JNDI;

	/**
	 * Máximo de threads de processamento de eventos.
	 */
	public static final int PROCESS_EVENT_MAX_THREAD;
	
	/**
	 * Tempo que será aguardado para verificar se a thread de processamento de eventos está viva. 2 MIN.
	 */
	public static final int PROCESS_EVENT_ALIVE_CHECK_TIME;
	
	/**
	 * Tempo que será aguardado para verificar passivamente se algo deve ser processado novamente. 1 MIN.
	 */
	public static final int PROCESS_EVENT_AUTO_TIME;

	
	private static ProcessCommand processCommand;
	
	static {
		String port = System.getProperty("digicon.port");
		if (port != null && isNumber(port)) {
			PORT = Integer.parseInt(port);
		} else {
			PORT = 7201;
		}
		
		String timeOutToCheckIdle = System.getProperty("digicon.agent.timeout_check_idle");
		if (timeOutToCheckIdle != null && isNumber(timeOutToCheckIdle) ) {
			TIME_OUT_TO_CHECK_IDLE = Integer.parseInt(timeOutToCheckIdle);	
		} else {
			TIME_OUT_TO_CHECK_IDLE = 30000;
		}
		 
		String timeOutIdle = System.getProperty("digicon.agent.timeout_idle");
		if (timeOutIdle != null && isNumber(timeOutIdle)) {
			TIME_OUT_IDLE = Integer.parseInt(timeOutIdle);
		} else {
			TIME_OUT_IDLE = 180000;
		}
		
		String jndi = System.getProperty("sered.jndi");
		if (jndi != null) {
			SERED_JNDI = jndi;
		} else {
			SERED_JNDI = "localhost:8180";
		}
		
		String processEventMaxThread = System.getProperty("sered.processevent.maxthread");
		if (processEventMaxThread != null && isNumber(processEventMaxThread)) {
			PROCESS_EVENT_MAX_THREAD = Integer.parseInt(processEventMaxThread);
		} else {
			PROCESS_EVENT_MAX_THREAD = 5;
		}
		
		String processEventAliveCheckTime = System.getProperty("sered.processevent.alivecheck");
		if (processEventAliveCheckTime != null && isNumber(processEventAliveCheckTime)) {
			PROCESS_EVENT_ALIVE_CHECK_TIME = Integer.parseInt(processEventAliveCheckTime);
		} else {
			PROCESS_EVENT_ALIVE_CHECK_TIME = 2 * 60 * 1000;
		}
		
		String processEventAutoTime = System.getProperty("sered.processevent.autotime");
		if (processEventAutoTime != null && isNumber(processEventAutoTime)) {
			PROCESS_EVENT_AUTO_TIME = Integer.parseInt(processEventAutoTime);
		} else {
			PROCESS_EVENT_AUTO_TIME = 1 * 60 * 1000;
		}
		
		processCommand = new ProcessCommand();
	}
	
	
	public static void main(String[] args) {
		startServer();
	}
	
	public static void startServer() {
		ServerSocket serverSocket = null;
		Socket socket = null;

		try {
			logger.info("Inicializando server na porta: " + PORT + " versão API Digicon Máxima: " + DigiconCommandParser.VERSAO_4);
			logger.info("Servidor SERED: " + SERED_JNDI);
			serverSocket = new ServerSocket(PORT);
		} catch (IOException e) {
			logger.error("Erro ao subir servidor na porta " + PORT, e);
			return;
		}
		
		process();
		
		while (true) {
			try {
				socket = serverSocket.accept();
				CommandThread commandThread = new CommandThread(socket);
				commandThread.start();
			} catch (Throwable e) {
				logger.error("Erro na comunicação com o device.", e);				
			}
		}
	}
	
	public static void process() {
		processCommand.process();
	}
	
	public static boolean isNumber(String text) {
		if(text == null || text.isEmpty())
			return false;
		
		for (char c : text.toCharArray())
	    {
	        if (!Character.isDigit(c)) return false;
	    }
	    return true;
	}

}
