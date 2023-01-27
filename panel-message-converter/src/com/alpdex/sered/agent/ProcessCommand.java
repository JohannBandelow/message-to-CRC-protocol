package com.alpdex.sered.digicon.agent;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;

import com.alpdex.sered.sales.ParkingMeterProcessDelegate;

public class ProcessCommand {
	
	private static final Logger logger = Logger.getLogger(ProcessCommand.class);
	private final Semaphore semaphore = new Semaphore(AppServer.PROCESS_EVENT_MAX_THREAD, true);
	
	/**
	 * Timer responsável por verificar de tempos em tempos se a THREAD de checagem da replicação está rodando.
	 */
	private Timer timer;
	
	/**
	 * Thread onde fica rodando o WHILE principal infinito
	 */
	private Thread thread = null;
	
	public ProcessCommand() {
		
		Calendar c = Calendar.getInstance();
		c.add(Calendar.SECOND, 10); //10 segundos para iniciar o timer. Este irá iniciar a thread do while.

		this.timer = new Timer("monitorProcessEventTimer", true);

		timer.schedule(new TimerTask()
		{

			@Override
			public void run()
			{
				// verifica se a thread de envio de e-mail está ativa
				isThreadAlive();
			}

		}, c.getTime(), (AppServer.PROCESS_EVENT_ALIVE_CHECK_TIME));

	}
	
	/**
	 * Método responsável por verificar se a thread do while está rodando.
	 */
	private synchronized void isThreadAlive() {
		if (this.thread == null || !this.thread.isAlive()) {
			logger.debug("Thread de verificacao de processamento de eventos iniciando/reiniciando... ");
			thread = new Thread(this.getRunnable());
			thread.setName("verifyProcessEvent");
			thread.start();
		}
	}
	
	/**
	 * Retorna a implementação da thread de processamento de eventos.
	 * @return
	 */
	private Runnable getRunnable() {
		Runnable run = new Runnable() {
			public void run() {
				try {
					tryAgain();
				} catch (Throwable e) {
					logger.error("Parada do mecanismo de verificação de processamento de evento", e);
				}
			}
		};

		return run;
	}

	/**
	 * Método responsável por solicitar o processamento. Este método roda em THREAD separada.
	 * @param user
	 * @param panes 
	 * @param phones 
	 * @param instanceCode
	 * @param instanceJNDI
	 */
	public void process() {
		
		try {
			semaphore.acquire();
		
			Thread t = new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						logger.debug("Enviando requisição para processar eventos no SERED");
						ParkingMeterProcessDelegate delegate = new ParkingMeterProcessDelegate(AppServer.SERED_JNDI);
						//chama serviço para processar pendências.
				        delegate.processParkingMeterEvent();
					} catch (Throwable e) {
						logger.error("Erro ao processar eventos.", e);
					} finally {
						semaphore.release();
					}
				}
				
			});
			
			t.start();
		
		} catch (InterruptedException e) {
			logger.error("Erro de interrupção de thread.", e);
		}
	}
	
	/**
	 * Método com o WHILE infinito que de tempos em tempos processa automaticamente o que pode estar pendente.
	 */
	private void tryAgain() {
		while(true) {
			
			process();
			
			synchronized(this) {
				try {
					//entra em modo de espera pelo tempo padrão, ou caso alguém notifique o objeto para continuar.
					this.wait(AppServer.PROCESS_EVENT_AUTO_TIME);
				} catch (InterruptedException e) {
					logger.error("Erro de interrupção de thread.", e);
				}
			}
		}
	}
}
