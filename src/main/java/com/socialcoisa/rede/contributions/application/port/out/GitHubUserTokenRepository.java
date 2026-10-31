package com.socialcoisa.rede.contributions.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.contributions.domain.GitHubUserToken;

public interface GitHubUserTokenRepository {

	Optional<GitHubUserToken> findByUserId(String userId);

	GitHubUserToken save(GitHubUserToken token);
}