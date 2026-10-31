package com.socialcoisa.rede.contributions.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor da verificação disparada quando alguém vincula o GitHub.
 *
 * Uma thread só, como no automod: a sincronização chama a API do GitHub e não pode competir com as
 * threads de requisição nem atrasar o retorno do OAuth.
 */
@Configuration(proxyBeanMethods = false)
public class GitHubSyncConfig {

	@Bean(name = "githubContributionSyncExecutor", destroyMethod = "shutdown")
	ExecutorService githubContributionSyncExecutor() {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "github-contribution-sync");
			thread.setDaemon(true);
			return thread;
		});
	}
}
