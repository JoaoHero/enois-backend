package com.socialcoisa.rede.jobs.application;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;

class ExternalJobRefreshSchedulerTest {

	private final ExternalJobImportService importService = mock(ExternalJobImportService.class);

	private final ExternalJobRepository repository = mock(ExternalJobRepository.class);

	@Test
	void primeiraCargaRodaNoBootSemSegurarAInicializacao() {
		when(importService.refresh()).thenReturn(1);
		ExternalJobRefreshScheduler agendador = new ExternalJobRefreshScheduler(importService, repository, true);

		agendador.seedIfEmpty();

		// Regressao: a carga nao pode depender de thread do pool comum — em pod de um nucleo o pool
		// nasce com paralelismo zero e a tarefa enfileirada nunca roda.
		verify(importService, timeout(5_000)).refresh();
	}

	@Test
	void desligadoNaoImportaNoBoot() {
		ExternalJobRefreshScheduler agendador = new ExternalJobRefreshScheduler(importService, repository, false);

		agendador.seedIfEmpty();

		verify(importService, after(300).never()).refresh();
	}

}