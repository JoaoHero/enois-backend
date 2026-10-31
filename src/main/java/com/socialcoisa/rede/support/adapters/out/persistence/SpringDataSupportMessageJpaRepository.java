package com.socialcoisa.rede.support.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSupportMessageJpaRepository extends JpaRepository<SupportMessageJpaEntity, String> {

	List<SupportMessageJpaEntity> findByRequestIdOrderByCreatedAtAsc(String requestId);

	Optional<SupportMessageJpaEntity> findByGithubCommentId(long githubCommentId);
}