package com.socialcoisa.rede.contributors.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.contributors.application.port.out.ContributorApplicationRepository;
import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;

@Repository
class ContributorApplicationPersistenceAdapter implements ContributorApplicationRepository {

	private final SpringDataContributorApplicationJpaRepository springData;

	ContributorApplicationPersistenceAdapter(SpringDataContributorApplicationJpaRepository springData) {
		this.springData = springData;
	}

	@Override
	public ContributorApplication save(ContributorApplication application) {
		return springData.save(ContributorApplicationJpaEntity.fromDomain(application)).toDomain();
	}

	@Override
	public List<ContributorApplication> list(ContributorApplicationStatus status, int limit) {
		PageRequest page = PageRequest.of(0, limit);
		List<ContributorApplicationJpaEntity> rows = status == null
			? springData.findAllByOrderByCreatedAtDesc(page)
			: springData.findByStatusOrderByCreatedAtDesc(status, page);
		return rows.stream().map(ContributorApplicationJpaEntity::toDomain).toList();
	}

	@Override
	public Optional<ContributorApplication> findById(String id) {
		return springData.findById(id).map(ContributorApplicationJpaEntity::toDomain);
	}

	@Override
	public Optional<ContributorApplication> findByUserId(String userId) {
		return springData.findByUserId(userId).map(ContributorApplicationJpaEntity::toDomain);
	}

	@Override
	public Optional<ContributorApplication> findLandingByGithub(String github) {
		return springData.findFirstByGithubAndUserIdIsNullOrderByCreatedAtDesc(github)
			.map(ContributorApplicationJpaEntity::toDomain);
	}
}
