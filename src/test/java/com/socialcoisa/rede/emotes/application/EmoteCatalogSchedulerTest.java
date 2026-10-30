package com.socialcoisa.rede.emotes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class EmoteCatalogSchedulerTest {

	private final EmoteCatalogService servico = mock(EmoteCatalogService.class);

	/**
	 * Sinal de que a thread de boot chamou `refresh()`. Nao da para esperar com
	 * `verify(servico, timeout(...)).refresh()`: o `refresh()` e `synchronized`, e a chamada de
	 * verificacao segura o monitor do mock enquanto espera. Se a thread de boot chega depois, fica
	 * travada nesse monitor ate o timeout e o teste falha (acontecia na suite inteira, sob carga).
	 */
	private final CountDownLatch chamou = new CountDownLatch(1);

	@Test
	void montaNoBootEmOutraThreadSemSegurarAInicializacao() throws Exception {
		EmoteCatalogScheduler agendador = new EmoteCatalogScheduler(servico, true);

		doAnswer(invocacao -> {
			chamou.countDown();
			return null;
		}).when(servico).refresh();

		long inicio = System.nanoTime();
		agendador.warmUpOnBoot();
		long decorrido = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

		// Regressao: o boot nao pode esperar as listagens do FFZ (segundos cada), nem depender da thread
		// do agendador comum. A janela do verify e folgada de proposito: a suite inteira roda em
		// paralelo com outras classes e 5s estouravam por contencao, nao por regressao.
		assertThat(decorrido).isLessThan(1_000);
		assertThat(chamou.await(15, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void erroQueNaoEExcecaoNoBootNaoEscapaDaThread() throws Exception {
		EmoteCatalogScheduler agendador = new EmoteCatalogScheduler(servico, true);
		doAnswer(invocacao -> {
			chamou.countDown();
			throw new NoClassDefFoundError("com/exemplo/Sumiu");
		}).when(servico).refresh();

		agendador.warmUpOnBoot();

		// Regressao: um Error matava a thread sem log e a montagem simplesmente nao acontecia.
		assertThat(chamou.await(15, TimeUnit.SECONDS)).isTrue();
		Thread.sleep(200);
		assertThat(Thread.getAllStackTraces().keySet().stream().map(Thread::getName))
			.doesNotContain("figurinhas-boot");
	}

	@Test
	void desligadoNaoMontaNoBoot() {
		EmoteCatalogScheduler agendador = new EmoteCatalogScheduler(servico, false);

		agendador.warmUpOnBoot();
		agendador.refreshDaily();

		verify(servico, after(300).never()).refresh();
	}

	@Test
	void rodadaAgendadaMontaUmaVezESegueDePeQuandoFalha() {
		EmoteCatalogScheduler agendador = new EmoteCatalogScheduler(servico, true);
		agendador.refreshDaily();
		verify(servico, times(1)).refresh();

		doThrow(new IllegalStateException("provedor fora do ar")).when(servico).refresh();
		agendador.refreshDaily();

		verify(servico, times(2)).refresh();
	}

}