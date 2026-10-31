package com.socialcoisa.rede.ops.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

/**
 * A busca do painel precisa alcançar um incidente que já saiu do topo.
 *
 * <p>Em 2026-10-26 um usuário reportou 500 no `/auth/2fa/verify` e, minutos depois, a busca
 * respondia "nenhum evento": a leitura só olhava as últimas ~400 linhas do arquivo. Quem investiga
 * chega depois do fato — o filtro (período ou termo) tem que ir fundo.</p>
 */
class MonitoringSearchWindowTests {

	@TempDir
	Path dir;

	@Test
	void buscaPorTermoAlcancaIncidenteForaDaJanelaRecente() {
		MonitoringService monitoring = service();
		String marcador = "incidente-" + UUID.randomUUID();
		monitoring.recordEvent("ERROR", "EXCEPTION", marcador, Map.of("origem", "teste"));
		// Ruído suficiente para empurrar o incidente para fora da janela curta (400 linhas).
		for (int i = 0; i < 600; i++) {
			monitoring.recordEvent("INFO", "REQUEST", "ruido " + i, Map.of());
		}
		flushAll(monitoring);

		assertThat(monitoring.events(null, null, marcador, null, null, null, 50))
			.extracting(MonitoringService.EventView::message)
			.containsExactly(marcador);
	}

	@Test
	void buscaPorPeriodoAlcancaIncidenteAntigo() {
		MonitoringService monitoring = service();
		String marcador = "janela-" + UUID.randomUUID();
		monitoring.recordEvent("ERROR", "EXCEPTION", marcador, Map.of());
		for (int i = 0; i < 600; i++) {
			monitoring.recordEvent("INFO", "REQUEST", "ruido " + i, Map.of());
		}
		flushAll(monitoring);

		String desde = java.time.Instant.now().minusSeconds(3600).toString();
		assertThat(monitoring.events("ERROR", null, null, desde, null, null, 50))
			.extracting(MonitoringService.EventView::message)
			.contains(marcador);
	}

	private MonitoringService service() {
		FileEventSink sink = new FileEventSink(dir.toString(), 7, JsonMapper.builder().build());
		return new MonitoringService(Clock.systemUTC(), null, sink, 100_000);
	}

	private static void flushAll(MonitoringService monitoring) {
		// O flush drena no máximo 200 por chamada.
		for (int i = 0; i < 10; i++) {
			monitoring.flush();
		}
	}
}
