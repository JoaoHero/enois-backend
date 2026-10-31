package com.socialcoisa.rede.ops.monitoring;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Eventos de monitorabilidade em arquivo JSONL na VPS, sem passar pelo banco.
 *
 * <p>Um arquivo por dia ({@code events-AAAA-MM-DD.jsonl}) com uma linha JSON por evento
 * (formato estável, campos do {@link MonitoringService.EventView}). A rotação apaga os dias
 * além da retenção. Leitura reversa: os dias mais novos primeiro, do fim para o começo,
 * parando quando o limite do painel é atingido — sem carregar o arquivo inteiro.</p>
 */
@Component
class FileEventSink {

	private static final Logger LOG = LoggerFactory.getLogger(FileEventSink.class);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final int MAX_LINE = 16_384;
	/** Bloco da leitura reversa; linhas inteiras são decodificadas, o resto fica no carry. */
	private static final int BLOCK = 64 * 1024;
	/** Teto de linhas varridas por consulta (paginação profunda é limitada por latência). */
	private static final int MAX_SCAN_LINES = 500_000;

	private final Path dir;
	private final ObjectMapper mapper;
	private final int retentionDays;

	private final Object writeLock = new Object();
	private volatile String openDay;
	private volatile BufferedWriter writer;

	FileEventSink(
			@Value("${rede.monitoring.file-dir:/app/data/monitoring}") String dir,
			@Value("${rede.monitoring.retention-days:7}") int retentionDays,
			ObjectMapper mapper) {
		this.dir = Paths.get(dir);
		this.mapper = mapper;
		this.retentionDays = Math.max(1, retentionDays);
	}

	/** Grava o evento; falha de disco nunca derruba a requisição. */
	void append(MonitoringService.EventView event) {
		String day = DAY.format(LocalDate.now(ZoneOffset.UTC));
		try {
			BufferedWriter out = writerFor(day);
			out.write(toJson(event));
			out.newLine();
			out.flush();
		}
		catch (IOException failure) {
			closeQuietly();
			LOG.warn("monitoramento em arquivo indisponivel: {}", failure.getMessage());
		}
	}

	/** Fecha o dia anterior e apaga dias além da retenção. */
	void rotate() {
		String today = DAY.format(LocalDate.now(ZoneOffset.UTC));
		synchronized (writeLock) {
			if (!today.equals(openDay)) {
				closeQuietly();
			}
		}
		if (!Files.isDirectory(dir)) {
			return;
		}
		LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "events-*.jsonl")) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				String day = name.length() >= 17 ? name.substring(7, 17) : "";
				try {
					if (!day.isBlank() && LocalDate.parse(day, DAY).isBefore(cutoff)) {
						Files.deleteIfExists(file);
					}
				}
				catch (RuntimeException ignored) {
					// Nome fora do padrão: não é nosso, não toca.
				}
			}
		}
		catch (IOException ignored) {
			// Rotação é melhor esforço; o próximo ciclo tenta de novo.
		}
	}

	/**
	 * Lê o histórico COMPLETO (todos os dias retidos), do mais novo para o mais antigo, até juntar
	 * {@code limit} eventos que passem no filtro. É o que permite a paginação: o cursor `before`
	 * continua de onde a página anterior parou, sem reler só o rabo do arquivo.
	 *
	 * @param before  só eventos estritamente mais antigos que este instante (cursor)
	 * @param stopAt  para a varredura ao cruzar esta data (início da janela pedida)
	 * @param filter  filtros do painel (nível, tipo, busca, período superior)
	 */
	List<MonitoringService.EventView> read(Instant before, Instant stopAt, int limit,
			Predicate<MonitoringService.EventView> filter) {
		int teto = Math.max(1, limit);
		List<MonitoringService.EventView> found = new ArrayList<>(teto);
		int[] scanned = { 0 };
		for (Path file : daysNewestFirst()) {
			boolean keepGoing = readFileBackward(file, line -> {
				if (++scanned[0] > MAX_SCAN_LINES) {
					return false;
				}
				MonitoringService.EventView event = fromJson(line);
				if (event == null) {
					return true;
				}
				Instant at = instantOf(event);
				if (before != null && !at.isBefore(before)) {
					return true;
				}
				if (stopAt != null && at.isBefore(stopAt)) {
					return false;
				}
				if (!filter.test(event)) {
					return true;
				}
				found.add(event);
				return found.size() < teto;
			});
			if (!keepGoing || found.size() >= teto) {
				break;
			}
		}
		return found;
	}

	private List<Path> daysNewestFirst() {
		List<Path> files = new ArrayList<>();
		if (!Files.isDirectory(dir)) {
			return files;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "events-*.jsonl")) {
			for (Path file : stream) {
				files.add(file);
			}
		}
		catch (IOException ignored) {
			return files;
		}
		files.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
		return files;
	}

	/**
	 * Percorre o arquivo de trás para frente em blocos, sem carregar o dia inteiro na memória.
	 * O `carry` guarda bytes de uma linha cortada entre blocos (e preserva UTF-8: só decodifica
	 * linhas inteiras). O consumidor devolve false para encerrar a varredura.
	 */
	private boolean readFileBackward(Path file, Predicate<String> lineConsumer) {
		byte[] buf = new byte[BLOCK];
		byte[] carry = new byte[0];
		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			long pos = raf.length();
			while (pos > 0) {
				int size = (int) Math.min(buf.length, pos);
				pos -= size;
				raf.seek(pos);
				raf.readFully(buf, 0, size);
				byte[] combined = new byte[size + carry.length];
				System.arraycopy(buf, 0, combined, 0, size);
				System.arraycopy(carry, 0, combined, size, carry.length);
				int lastNewline = lastNewline(combined, combined.length - 1);
				if (lastNewline < 0) {
					carry = combined;
					continue;
				}
				carry = Arrays.copyOfRange(combined, lastNewline + 1, combined.length);
				int end = lastNewline;
				while (end >= 0) {
					int start = lastNewline(combined, end - 1) + 1;
					String line = new String(combined, start, end - start, StandardCharsets.UTF_8);
					if (line.endsWith("\r")) {
						line = line.substring(0, line.length() - 1);
					}
					if (!line.isBlank() && !lineConsumer.test(line)) {
						return false;
					}
					end = start - 1;
				}
			}
			if (carry.length > 0) {
				String line = new String(carry, StandardCharsets.UTF_8).strip();
				if (!line.isEmpty() && !lineConsumer.test(line)) {
					return false;
				}
			}
			return true;
		}
		catch (IOException ignored) {
			return true;
		}
	}

	private static int lastNewline(byte[] data, int from) {
		for (int i = Math.min(from, data.length - 1); i >= 0; i--) {
			if (data[i] == '\n') {
				return i;
			}
		}
		return -1;
	}

	private BufferedWriter writerFor(String day) throws IOException {
		synchronized (writeLock) {
			if (writer != null && day.equals(openDay)) {
				return writer;
			}
			closeQuietly();
			Files.createDirectories(dir);
			writer = Files.newBufferedWriter(dir.resolve("events-" + day + ".jsonl"),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			openDay = day;
			return writer;
		}
	}

	private void closeQuietly() {
		synchronized (writeLock) {
			if (writer == null) {
				return;
			}
			try {
				writer.close();
			}
			catch (IOException ignored) {
				// Fechar é melhor esforço.
			}
			writer = null;
			openDay = null;
		}
	}

	private String toJson(MonitoringService.EventView event) {
		StringBuilder json = new StringBuilder(512);
		json.append("{\"id\":").append(quoted(event.id()));
		json.append(",\"createdAt\":").append(quoted(event.createdAt()));
		json.append(",\"level\":").append(quoted(event.level()));
		json.append(",\"type\":").append(quoted(event.type()));
		json.append(",\"message\":").append(quoted(event.message()));
		json.append(",\"requestId\":").append(quoted(event.requestId()));
		json.append(",\"method\":").append(quoted(event.method()));
		json.append(",\"path\":").append(quoted(event.path()));
		json.append(",\"route\":").append(quoted(event.route()));
		json.append(",\"status\":").append(event.status() == null ? "null" : event.status().toString());
		json.append(",\"durationMs\":")
			.append(event.durationMs() == null ? "null" : event.durationMs().toString());
		json.append(",\"userId\":").append(quoted(event.userId()));
		json.append(",\"clientIp\":").append(quoted(event.clientIp()));
		json.append(",\"deviceId\":").append(quoted(event.deviceId()));
		json.append(",\"metadata\":").append(quoted(event.metadata()));
		json.append('}');
		String line = json.toString();
		return line.length() > MAX_LINE ? line.substring(0, MAX_LINE) : line;
	}

	private MonitoringService.EventView fromJson(String line) {
		try {
			JsonNode node = mapper.readTree(line);
			return new MonitoringService.EventView(
				text(node, "id"), text(node, "createdAt"), text(node, "level"), text(node, "type"),
				text(node, "message"), text(node, "requestId"), text(node, "method"), text(node, "path"),
				text(node, "route"),
				node.has("status") && !node.get("status").isNull() ? (Integer) node.get("status").asInt() : null,
				node.has("durationMs") && !node.get("durationMs").isNull()
					? (Long) node.get("durationMs").asLong()
					: null,
				text(node, "userId"), text(node, "clientIp"), text(node, "deviceId"),
				text(node, "metadata"));
		}
		catch (RuntimeException failure) {
			return null;
		}
	}

	private static String text(JsonNode node, String field) {
		if (!node.has(field) || node.get(field).isNull()) {
			return null;
		}
		return node.get(field).asString();
	}

	private static String quoted(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder out = new StringBuilder(value.length() + 2);
		out.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
		return out.toString();
	}

	static Instant instantOf(MonitoringService.EventView event) {
		try {
			return Instant.parse(event.createdAt());
		}
		catch (RuntimeException failure) {
			return Instant.EPOCH;
		}
	}
}
