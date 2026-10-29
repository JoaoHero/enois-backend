package com.socialcoisa.rede.emotes.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monta o catalogo de figurinhas fora do caminho da requisicao: uma vez no boot e, depois, de 3 em
 * 3 horas.
 *
 * O boot serve o retrato embarcado na hora; a montagem completa (que consulta os provedores e leva
 * alguns segundos, por causa das listagens do FFZ) roda em segundo plano para nao atrasar o readiness
 * do pod. Falha aqui nao derruba nada: o catalogo anterior continua servindo.
 */
@Component
public class EmoteCatalogScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(EmoteCatalogScheduler.class);

	private final EmoteCatalogService catalogService;
	private final boolean enabled;

	public EmoteCatalogScheduler(EmoteCatalogService catalogService,
			@Value("${rede.emotes.refresh-enabled:true}") boolean enabled) {
		this.catalogService = catalogService;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmUpOnBoot() {
		if (!enabled) {
			return;
		}
		// Thread propria, NAO o pool comum: com um nucleo (`-XX:ActiveProcessorCount=1`) o pool comum
		// nasce com paralelismo zero e a tarefa enfileirada nunca roda — o catalogo ficava no retrato
		// embarcado ate o refresh agendado, tres horas depois. Foi o que aconteceu em QA em 2026-10-29:
		// o pod subiu as 02:11 e o catalogo so saiu do retrato as 03:00, na rodada do cron.
		Thread.ofVirtual().name("figurinhas-boot").start(this::buildOnBoot);
	}

	private void buildOnBoot() {
		try {
			catalogService.refresh();
		}
		catch (RuntimeException falha) {
			LOG.warn("Falha ao montar o catalogo de figurinhas no boot (segue com o embarcado).", falha);
		}
	}

	/**
	 * Padrao: de 3 em 3 horas. Cada rodada SOMA o que cada fonte trouxe (ver `EmoteCatalogService`);
	 * fonte que falha mantem a copia anterior, entao uma rodada ruim nao tira figurinha de ninguem.
	 */
	@Scheduled(cron = "${rede.emotes.refresh-cron:0 0 */3 * * *}")
	public void refreshDaily() {
		if (!enabled) {
			return;
		}
		try {
			catalogService.refresh();
		}
		catch (RuntimeException falha) {
			LOG.warn("Falha ao montar o catalogo de figurinhas (segue com o anterior).");
		}
	}
}