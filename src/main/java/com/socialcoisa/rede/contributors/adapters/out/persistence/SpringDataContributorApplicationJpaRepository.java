package com.socialcoisa.rede.contributors.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;

interface SpringDataContributorApplicationJpaRepository
		extends JpaRepository<ContributorApplicationJpaEntity, String> {

	List<ContributorApplicationJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	List<ContributorApplicationJpaEntity> findByStatusOrderByCreatedAtDesc(ContributorApplicationStatus status,
			Pageable pageable);

	Optional<ContributorApplicationJpaEntity> findByUserId(String userId);

	Optional<ContributorApplicationJpaEntity> findFirstByGithubAndUserIdIsNullOrderByCreatedAtDesc(String github);
}
