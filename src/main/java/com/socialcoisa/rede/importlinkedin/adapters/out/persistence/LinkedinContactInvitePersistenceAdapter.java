package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.importlinkedin.application.port.out.LinkedinContactInviteRepository;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactInvite;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinInviteStatus;

@Repository
class LinkedinContactInvitePersistenceAdapter implements LinkedinContactInviteRepository {

	private final SpringDataLinkedinContactInviteJpaRepository repository;

	LinkedinContactInvitePersistenceAdapter(SpringDataLinkedinContactInviteJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<LinkedinContactInvite> findByUserAndKey(String userId, String nameKey) {
		return repository.findByUserIdAndNameKey(userId, nameKey)
			.map(LinkedinContactInviteJpaEntity::toDomain);
	}

	@Override
	public List<LinkedinContactInvite> findQueuedByUser(String userId, int limit) {
		return repository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, LinkedinInviteStatus.QUEUED,
			PageRequest.of(0, Math.max(1, limit))).stream().map(LinkedinContactInviteJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<LinkedinContactInvite> findByUser(String userId, int limit) {
		return repository.findByUserIdOrderByCreatedAtAsc(userId, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(LinkedinContactInviteJpaEntity::toDomain).toList();
	}

	@Override
	public int countByUser(String userId) {
		return repository.countByUserId(userId);
	}

	@Override
	public int countByUserAndStatus(String userId, LinkedinInviteStatus status) {
		return repository.countByUserIdAndStatus(userId, status);
	}

	@Override
	public LinkedinContactInvite save(LinkedinContactInvite invite) {
		return repository.save(LinkedinContactInviteJpaEntity.fromDomain(invite)).toDomain();
	}
}