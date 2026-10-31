package com.socialcoisa.rede.contributions.adapters.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.contributions.application.port.out.GitHubUserTokenRepository;
import com.socialcoisa.rede.contributions.domain.GitHubUserToken;

@Repository
class GitHubUserTokenPersistenceAdapter implements GitHubUserTokenRepository {

	private final SpringDataGitHubUserTokenJpaRepository repository;

	GitHubUserTokenPersistenceAdapter(SpringDataGitHubUserTokenJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<GitHubUserToken> findByUserId(String userId) {
		return repository.findById(userId).map(GitHubUserTokenJpaEntity::toDomain);
	}

	@Override
	public GitHubUserToken save(GitHubUserToken token) {
		return repository.save(GitHubUserTokenJpaEntity.fromDomain(token)).toDomain();
	}
}