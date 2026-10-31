package com.socialcoisa.rede.support.adapters.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.support.application.SupportGithubConfig;
import com.socialcoisa.rede.support.application.SupportWebhookService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook do repositório PRIVADO de issues do suporte. A porta pública é
 * {@code https://enois.ia.br/api/webhooks/github} (o gateway remove o /api).
 *
 * A rota é uma escrita sem sessão de propósito (o GitHub não tem sessão no app), então a
 * autenticação é o HMAC do próprio GitHub: {@code X-Hub-Signature-256} conferido com o segredo
 * do webhook. Sem segredo configurado a rota recusa tudo (fail-closed). Qualquer outro evento
 * que não seja uma issue fechada/reaberta responde 200 e é ignorado.
 */
@RestController
class GithubWebhookController {

	private static final Logger LOG = LoggerFactory.getLogger(GithubWebhookController.class);

	private final SupportWebhookService webhookService;
	private final ObjectMapper objectMapper;
	private final SupportGithubConfig config;

	GithubWebhookController(SupportWebhookService webhookService, ObjectMapper objectMapper,
			SupportGithubConfig config) {
		this.webhookService = webhookService;
		this.objectMapper = objectMapper;
		this.config = config;
	}

	@PostMapping("/webhooks/github")
	ResponseEntity<Void> receive(@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestBody byte[] body) {
		String secret = config.webhookSecret();
		if (!StringUtils.hasText(secret)) {
			return ResponseEntity.status(503).build();
		}
		if (!validSignature(secret, signature, body)) {
			return ResponseEntity.status(401).build();
		}
		try {
			JsonNode payload = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
			if ("issue_comment".equals(event)) {
				String action = payload.path("action").asString("");
				String repository = payload.path("repository").path("full_name").asString("");
				if (!webhookService.acceptsRepository(repository) || !"created".equals(action)) {
					return ResponseEntity.ok().build();
				}
				int issueNumber = payload.path("issue").path("number").asInt(0);
				long commentId = payload.path("comment").path("id").asLong(0);
				String author = payload.path("comment").path("user").path("login").asString("");
				String commentBody = payload.path("comment").path("body").asString("");
				if (issueNumber > 0 && commentId > 0) {
					webhookService.applyIssueComment(issueNumber, commentId, author, commentBody);
				}
				return ResponseEntity.ok().build();
			}
			if (!"issues".equals(event)) {
				return ResponseEntity.ok().build();
			}
			String action = payload.path("action").asString("");
			String repository = payload.path("repository").path("full_name").asString("");
			if (!webhookService.acceptsRepository(repository)) {
				LOG.warn("Webhook de issues ignorado: repositório {}", repository);
				return ResponseEntity.ok().build();
			}
			int number = payload.path("issue").path("number").asInt(0);
			String state = payload.path("issue").path("state").asString("");
			if (number <= 0) {
				return ResponseEntity.ok().build();
			}
			if ("closed".equals(action) || "reopened".equals(action)) {
				webhookService.applyIssueState(number, state);
			}
			return ResponseEntity.ok().build();
		}
		catch (RuntimeException malformed) {
			// Corpo assinado mas inesperado: não adianta o GitHub reenviar.
			LOG.warn("Webhook do GitHub com corpo inválido: {}", malformed.getClass().getSimpleName());
			return ResponseEntity.ok().build();
		}
	}

	private boolean validSignature(String secret, String signature, byte[] body) {
		if (!StringUtils.hasText(signature) || !signature.startsWith("sha256=")) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
			return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
					signature.getBytes(StandardCharsets.US_ASCII));
		}
		catch (Exception failure) {
			return false;
		}
	}
}
