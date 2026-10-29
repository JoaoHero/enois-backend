package com.socialcoisa.rede.emotes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class EmoteCatalogSchedulerTest {

	private final EmoteCatalogService servico = mock(EmoteCatalogService.class);

	@Test
	void montaNoBootEmOutraThreadSemSegurarAInicializacao() {
		EmoteCatalogScheduler agendador = new EmoteCatalogScheduler(servico, true);

		long inicio = System.nanoTime();
		agendador.warmUpOnBoot();
		long decorrido = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

		// Regressao: o boot nao pode esperar as listagens do FFZ (segundos cada), nem depender de uma
		// thread do pool comum que pode nao existir (pod de um nucleo = paralelismo zero).
		assertThat(decorrido).isLessThan(1_000);
		verify(servico, timeout(5_000)).refresh();
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