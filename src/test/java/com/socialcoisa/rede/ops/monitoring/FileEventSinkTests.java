package com.socialcoisa.rede.ops.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

/**
 * O leitor do arquivo precisa alcançar o histórico inteiro e paginar pelo cursor — não só o rabo
 * do dia mais novo. Foi o bug do painel: "não busca nem pagina" e reiniciar a página "resetava".
 */
class FileEventSinkTests {

	@TempDir
	Path dir;

	private FileEventSink newSink() {
		return new FileEventSink(dir.toString(), 7, new ObjectMapper());
	}

	private void writeDay(String day, int count) throws Exception {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			// Append-only: o arquivo real cresce em ordem cronológica (o mais novo no fim).
			String at = String.format("%sT%02d:%02d:00Z", day, 11 + (i / 60), i % 60);
			lines.add("{\"id\":\"" + day + "-" + i + "\",\"createdAt\":\"" + at + "\",\"level\":\""
				+ (i % 10 == 0 ? "ERROR" : "INFO") + "\",\"type\":\"HTTP\",\"message\":\"evento " + i
				+ "\",\"path\":\"/api/x\"}");
		}
		Files.write(dir.resolve("events-" + day + ".jsonl"), lines, StandardCharsets.UTF_8);
	}

	@Test
	void paginaPeloCursorAtravessandoOsDias() throws Exception {
		writeDay("2026-10-28", 120);
		writeDay("2026-10-27", 120);
		FileEventSink sink = newSink();

		List<MonitoringService.EventView> first = sink.read(null, null, 100, event -> true);
		assertThat(first).hasSize(100);
		assertThat(first.get(0).createdAt()).startsWith("2026-10-28");

		Instant cursor = Instant.parse(first.get(first.size() - 1).createdAt());
		List<MonitoringService.EventView> second = sink.read(cursor, null, 100, event -> true);
		assertThat(second).hasSize(100);
		assertThat(Instant.parse(second.get(0).createdAt()))
			.isBefore(Instant.parse(first.get(first.size() - 1).createdAt()));

		Instant cursor2 = Instant.parse(second.get(second.size() - 1).createdAt());
		List<MonitoringService.EventView> third = sink.read(cursor2, null, 100, event -> true);
		assertThat(third).hasSize(40);
		assertThat(third.get(third.size() - 1).createdAt()).startsWith("2026-10-27");
	}

	@Test
	void filtraPorNivelDentroDoArquivoInteiro() throws Exception {
		writeDay("2026-10-28", 60);
		FileEventSink sink = newSink();

		List<MonitoringService.EventView> errors = sink.read(null, null, 100,
			event -> "ERROR".equals(event.level()));

		assertThat(errors).hasSize(6);
		assertThat(errors).allMatch(event -> "ERROR".equals(event.level()));
	}

	@Test
	void paraQuandoCruzaOInicioDaJanela() throws Exception {
		writeDay("2026-10-28", 30);
		writeDay("2026-10-27", 30);
		FileEventSink sink = newSink();

		List<MonitoringService.EventView> onlyToday = sink.read(null, Instant.parse("2026-10-28T00:00:00Z"),
			100, event -> true);

		assertThat(onlyToday).hasSize(30);
		assertThat(onlyToday).allMatch(event -> event.createdAt().startsWith("2026-10-28"));
	}
}