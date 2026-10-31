package com.socialcoisa.rede.support.adapters.out.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.support.application.SupportGithubConfig;
import com.socialcoisa.rede.support.application.port.out.GithubIssuesGateway;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Issues do suporte no GitHub. O token fica cifrado no banco; sem ele o gateway nasce desligado
 * ({@link #enabled()} falso) e o suporte continua apenas com e-mail e painel.
 *
 * Falha de rede/API nunca propaga: o ticket e a fonte da verdade local e o espelho e melhor
 * esforco — por isso tudo aqui devolve Optional/boolean em vez de lancar.
 */
@Component
class GithubIssuesClient implements GithubIssuesGateway {

	private static final Logger LOG = LoggerFactory.getLogger(GithubIssuesClient.class);

	private final ObjectMapper objectMapper;
	private final SupportGithubConfig config;
	private final HttpClient http;
	private final String apiBase;

	GithubIssuesClient(ObjectMapper objectMapper, SupportGithubConfig config,
			@Value("${rede.support.github.api-base:https://api.github.com}") String apiBase) {
		this.objectMapper = objectMapper;
		this.config = config;
		this.apiBase = apiBase == null ? "" : apiBase.trim();
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public Optional<IssueRef> createIssue(String title, String body) {
		if (!enabled()) {
			return Optional.empty();
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", title);
		payload.put("body", body);
		Optional<JsonNode> response = call("POST", "/repos/" + config.repository() + "/issues", payload);
		if (response.isEmpty()) {
			return Optional.empty();
		}
		JsonNode issue = response.get();
		int number = issue.path("number").asInt(0);
		String url = issue.path("html_url").asString("");
		if (number <= 0) {
			return Optional.empty();
		}
		return Optional.of(new IssueRef(number, url == null || url.isBlank() ? null : url));
	}

	@Override
	public boolean closeIssue(int issueNumber) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("state", "closed");
		payload.put("state_reason", "completed");
		return call("PATCH", "/repos/" + config.repository() + "/issues/" + issueNumber, payload).isPresent();
	}

	@Override
	public boolean reopenIssue(int issueNumber) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("state", "open");
		return call("PATCH", "/repos/" + config.repository() + "/issues/" + issueNumber, payload).isPresent();
	}

	@Override
	public Optional<Long> comment(int issueNumber, String body, String tokenOverride) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("body", body);
		return call("POST", "/repos/" + config.repository() + "/issues/" + issueNumber + "/comments", payload,
			tokenOverride).map(node -> node.path("id").asLong(0)).filter(id -> id > 0);
	}

	private Optional<JsonNode> call(String method, String path, Map<String, Object> payload) {
		return call(method, path, payload, null);
	}

	private Optional<JsonNode> call(String method, String path, Map<String, Object> payload, String tokenOverride) {
		if (!enabled()) {
			return Optional.empty();
		}
		String token = tokenOverride == null || tokenOverride.isBlank() ? config.token() : tokenOverride;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
				.timeout(Duration.ofSeconds(6))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + token)
				.header("X-GitHub-Api-Version", "2022-11-28")
				.header("User-Agent", "enois-suporte/1.0")
				.header("Content-Type", "application/json")
				.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				// Nunca loga o corpo: o pedido carrega dado pessoal e o token esta no cabecalho.
				LOG.warn("GitHub respondeu {} em {} {}", response.statusCode(), method, path);
				return Optional.empty();
			}
			return Optional.of(objectMapper.readTree(response.body()));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
		catch (Exception failure) {
			LOG.warn("Falha ao falar com o GitHub ({} {}): {}", method, path, failure.getClass().getSimpleName());
			return Optional.empty();
		}
	}
}
