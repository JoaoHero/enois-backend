package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.importlinkedin.domain.LinkedinInviteStatus;

interface SpringDataLinkedinContactInviteJpaRepository
		extends JpaRepository<LinkedinContactInviteJpaEntity, String> {

	Optional<LinkedinContactInviteJpaEntity> findByUserIdAndNameKey(String userId, String nameKey);

	List<LinkedinContactInviteJpaEntity> findByUserIdAndStatusOrderByCreatedAtAsc(String userId,
			LinkedinInviteStatus status, Pageable pageable);

	List<LinkedinContactInviteJpaEntity> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);

	int countByUserId(String userId);

	int countByUserIdAndStatus(String userId, LinkedinInviteStatus status);
}