package com.socialcoisa.rede.contributions.adapters.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.contributions.application.GitHubContributionSyncService;
import com.socialcoisa.rede.contributions.application.GitHubIdentityService;
import com.socialcoisa.rede.shared.security.UserRateLimiter;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/** Estado do vínculo GitHub da conta logada (a vinculação em si usa o OAuth existente). */
@RestController
class GitHubIdentityController {

	private final GitHubIdentityService identities;
	private final GitHubContributionSyncService sync;
	private final UserRateLimiter rateLimiter;
	private final CurrentUserResolver currentUserResolver;

	GitHubIdentityController(GitHubIdentityService identities, GitHubContributionSyncService sync,
			UserRateLimiter rateLimiter, CurrentUserResolver currentUserResolver) {
		this.identities = identities;
		this.sync = sync;
		this.rateLimiter = rateLimiter;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/me/github")
	GitHubIdentityService.GitHubIdentityView status(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return identities.status(user.id().value());
	}

	/**
	 * Verificação sob demanda: refaz a leitura das contribuições do vínculo e recalcula os selos.
	 * É o botão "Verificar contribuições" da tela de segurança — o mesmo trabalho do vínculo
	 * automático, para quando a pessoa quer tentar de novo sem esperar.
	 */
	@PostMapping("/me/github/sync")
	SyncResult sync(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String githubUserId = identities.githubUserId(user.id().value());
		if (githubUserId == null) {
			throw new ApiException(409, "GITHUB_NOT_LINKED",
				"Vincule sua conta do GitHub antes de verificar as contribuições.");
		}
		rateLimiter.require(user.id().value(), UserRateLimiter.SYNC_GITHUB);
		var report = sync.syncForGithubUser(githubUserId);
		return new SyncResult(report.created() + report.updated(),
			report.issuesCreated() + report.issuesUpdated(), report.sealsRecalculated(),
			report.rateLimited());
	}

	/** Só o que a tela mostra; detalhes internos da varredura não saem para o cliente. */
	record SyncResult(int contributions, int issues, int seals, boolean rateLimited) {
	}
}
