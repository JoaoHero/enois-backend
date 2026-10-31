package com.socialcoisa.rede.contributions.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.contributions.application.port.out.GitHubGateway;
import com.socialcoisa.rede.contributions.application.port.out.GitHubPullRequest;
import com.socialcoisa.rede.contributions.application.port.out.GitHubRepositoryRegistry;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Fila de revisão de PRs abertos dos repositórios elegíveis.
 *
 * <p>A revisão sai em nome de quem tem o vínculo GitHub com escrita; sem ele cai no token de
 * serviço (se tiver permissão) e, se nenhum dos dois puder, responde erro explícito — nada de
 * fingir que aprovou.</p>
 */
@Service
public class PullRequestReviewService {

	private static final Logger LOG = LoggerFactory.getLogger(PullRequestReviewService.class);
	private static final int MAX_COMMENT = 2000;

	private final GitHubGateway gateway;
	private final GitHubRepositoryRegistry repositories;
	private final GitHubUserTokenService tokens;

	public PullRequestReviewService(GitHubGateway gateway, GitHubRepositoryRegistry repositories,
			GitHubUserTokenService tokens) {
		this.gateway = gateway;
		this.repositories = repositories;
		this.tokens = tokens;
	}

	public List<OpenPullRequestView> open(int limit) {
		int capped = Math.max(1, Math.min(limit, 200));
		List<OpenPullRequestView> all = new ArrayList<>();
		for (var repository : repositories.findAll()) {
			if (!repository.enabled()) {
				continue;
			}
			try {
				for (GitHubPullRequest pull : gateway.listOpenPullRequests(repository.repository(), 2)) {
					all.add(OpenPullRequestView.from(repository.repository(), pull));
				}
			}
			catch (RuntimeException failure) {
				LOG.warn("Falha ao listar PRs abertos de {}: {}", repository.repository(),
					failure.getClass().getSimpleName());
			}
		}
		all.sort(Comparator.comparing(OpenPullRequestView::updatedAt,
			Comparator.nullsLast(Comparator.reverseOrder())));
		return all.stream().limit(capped).toList();
	}

	public void review(String userId, String repository, long number, String decision, String comment) {
		boolean known = repositories.findAll().stream()
			.anyMatch(item -> item.enabled() && item.repository().equals(repository));
		if (!known) {
			throw new ApiException(400, "UNKNOWN_REPOSITORY", "Repositório não está na lista elegível.");
		}
		String event = switch (decision == null ? "" : decision.trim().toUpperCase()) {
			case "APPROVE" -> "APPROVE";
			case "REQUEST_CHANGES" -> "REQUEST_CHANGES";
			default -> throw new ApiException(400, "INVALID_REVIEW_DECISION",
				"Decisão inválida: use APPROVE ou REQUEST_CHANGES.");
		};
		String clean = comment == null ? null : comment.trim();
		if ("REQUEST_CHANGES".equals(event) && !StringUtils.hasText(clean)) {
			throw new ApiException(400, "REVIEW_COMMENT_REQUIRED",
				"Explique o que precisa mudar antes de pedir alterações.");
		}
		if (clean != null && clean.length() > MAX_COMMENT) {
			throw new ApiException(400, "REVIEW_COMMENT_TOO_LONG",
				"O comentário da revisão deve ter no máximo " + MAX_COMMENT + " caracteres.");
		}
		String userToken = tokens.accessToken(userId).orElse(null);
		boolean done = gateway.submitReview(repository, number, event, clean, userToken);
		if (!done && userToken != null) {
			done = gateway.submitReview(repository, number, event, clean, null);
		}
		if (!done) {
			throw new ApiException(502, "GITHUB_REVIEW_FAILED",
				"O GitHub não aceitou a revisão. Vincule seu GitHub com permissão de escrita e tente de novo.");
		}
	}

	public record OpenPullRequestView(String repository, long number, String title, String url,
			String authorLogin, String authorAvatarUrl, Instant createdAt, Instant updatedAt) {
		static OpenPullRequestView from(String repository, GitHubPullRequest pull) {
			String login = pull.authorLogin();
			String avatar = StringUtils.hasText(login) ? "https://github.com/" + login + ".png?size=80" : null;
			return new OpenPullRequestView(repository, pull.number(), pull.title(), pull.url(), login, avatar,
				pull.openedAt(), pull.updatedAt());
		}
	}
}