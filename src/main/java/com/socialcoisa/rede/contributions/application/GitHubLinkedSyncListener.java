package com.socialcoisa.rede.contributions.application;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Verifica as contribuições assim que o vínculo GitHub é concluído.
 *
 * <p>Sem isso, conectar a conta não mostrava nada: os selos só apareciam na próxima varredura
 * (que nasce desligada). Roda depois do commit e fora da thread da requisição — falha no GitHub
 * nunca desfaz o vínculo, e a pessoa vê o estado da sincronização na tela de segurança.</p>
 */
@Component
class GitHubLinkedSyncListener {

	private static final Logger LOG = LoggerFactory.getLogger(GitHubLinkedSyncListener.class);

	private final GitHubContributionSyncService sync;
	private final Executor executor;
	private final boolean enabled;

	GitHubLinkedSyncListener(GitHubContributionSyncService sync,
			@Qualifier("githubContributionSyncExecutor") Executor executor,
			@Value("${rede.github.sync-on-link-enabled:true}") boolean enabled) {
		this.sync = sync;
		this.executor = executor;
		this.enabled = enabled;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onLinked(GitHubIdentityLinkedEvent event) {
		if (!enabled) {
			return;
		}
		executor.execute(() -> {
			try {
				var report = sync.syncForGithubUser(event.githubUserId());
				LOG.info("github_sync_on_link user={} prs={} issues={} seals={}",
					event.userId(), report.created() + report.updated(),
					report.issuesCreated() + report.issuesUpdated(), report.sealsRecalculated());
			}
			catch (RuntimeException failure) {
				LOG.warn("github_sync_on_link_failed user={} error={}", event.userId(), failure.getMessage());
			}
		});
	}
}
