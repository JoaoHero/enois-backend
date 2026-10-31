package com.socialcoisa.rede.contributions.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGitHubUserTokenJpaRepository extends JpaRepository<GitHubUserTokenJpaEntity, String> {
}