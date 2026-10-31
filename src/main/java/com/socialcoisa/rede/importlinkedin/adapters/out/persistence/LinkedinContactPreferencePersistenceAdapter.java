package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.importlinkedin.application.port.out.LinkedinContactPreferenceRepository;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactPreference;

@Repository
class LinkedinContactPreferencePersistenceAdapter implements LinkedinContactPreferenceRepository {

	private final SpringDataLinkedinContactPreferenceJpaRepository repository;

	LinkedinContactPreferencePersistenceAdapter(SpringDataLinkedinContactPreferenceJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<LinkedinContactPreference> findByUserId(String userId) {
		return repository.findById(userId).map(LinkedinContactPreferenceJpaEntity::toDomain);
	}

	@Override
	public LinkedinContactPreference save(LinkedinContactPreference preference) {
		return repository.save(LinkedinContactPreferenceJpaEntity.fromDomain(preference)).toDomain();
	}

	@Override
	public List<String> findUserIdsWithAutoInvite() {
		return repository.findUserIdsWithAutoInvite();
	}
}