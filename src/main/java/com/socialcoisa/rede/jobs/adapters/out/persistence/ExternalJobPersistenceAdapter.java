package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.domain.ExternalJob;

@Repository
class ExternalJobPersistenceAdapter implements ExternalJobRepository {

	private final SpringDataExternalJobJpaRepository repository;

	ExternalJobPersistenceAdapter(SpringDataExternalJobJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<ExternalJob> findById(String id) {
		return repository.findById(id).map(ExternalJobJpaEntity::toDomain);
	}

	@Override
	public Optional<ExternalJob> findBySource(String platform, String externalId) {
		return repository.findByPlatformAndExternalId(platform, externalId).map(ExternalJobJpaEntity::toDomain);
	}

	@Override
	public void save(ExternalJob job) {
		repository.save(ExternalJobJpaEntity.fromDomain(job));
	}

	@Override
	public Optional<ExternalJob> findByCanonicalUrl(String canonicalUrl) {
		return repository.findFirstByCanonicalUrl(canonicalUrl).map(ExternalJobJpaEntity::toDomain);
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> search(String query, int limit) {
		// `%` e `_` do texto viram literais: sem isso, buscar "_" casaria com tudo.
		String escaped = com.socialcoisa.rede.shared.web.LikePattern.escape(query.toLowerCase(java.util.Locale.ROOT));
		return repository.search("%" + escaped + "%", PageRequest.of(0, Math.max(1, limit))).stream()
			.map(ExternalJobJpaEntity::toDomain)
			.toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> findByPlatform(String platform, String query, int limit) {
		PageRequest page = PageRequest.of(0, Math.max(1, limit));
		if (query == null || query.isBlank()) {
			return repository.findByPlatformOrderByLastCheckedAtDesc(platform, page).stream()
				.map(ExternalJobJpaEntity::toDomain).toList();
		}
		String escaped = com.socialcoisa.rede.shared.web.LikePattern.escape(query.toLowerCase(java.util.Locale.ROOT));
		return repository.searchByPlatform(platform, "%" + escaped + "%", page).stream()
			.map(ExternalJobJpaEntity::toDomain).toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable(value = "externalJobs",
		key = "'excluding:' + #platform + ':' + #limit")
	public List<ExternalJob> findRecentExcludingPlatform(String platform, int limit) {
		return repository.findByPlatformNotOrderByLastCheckedAtDesc(platform,
			PageRequest.of(0, Math.max(1, limit))).stream().map(ExternalJobJpaEntity::toDomain).toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable(value = "externalJobs",
		key = "'excluding:' + #platform + ':' + (#query ?: '') + ':' + #limit")
	public List<ExternalJob> searchExcludingPlatform(String platform, String query, int limit) {
		String escaped = com.socialcoisa.rede.shared.web.LikePattern.escape(query.toLowerCase(java.util.Locale.ROOT));
		return repository.searchExcludingPlatform(platform, "%" + escaped + "%",
			PageRequest.of(0, Math.max(1, limit))).stream().map(ExternalJobJpaEntity::toDomain).toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<String> distinctPlatforms() {
		return repository.distinctPlatforms();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> findByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return repository.findByIdIn(ids).stream().map(ExternalJobJpaEntity::toDomain).toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> findRecent(int limit) {
		return repository.findAllByOrderByLastCheckedAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
			.map(ExternalJobJpaEntity::toDomain)
			.toList();
	}

	/** Sem cache: e a lista "minhas vagas externas", pessoal e curta — frescor importa mais. */
	@Override
	public List<ExternalJob> findBySubmitter(String userId) {
		return repository.findBySubmittedByOrderByImportedAtDesc(userId).stream()
			.map(ExternalJobJpaEntity::toDomain)
			.toList();
	}

	@Override
	public void delete(String id) {
		repository.deleteById(id);
	}
}
