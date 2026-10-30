package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.companies.application.port.out.CompanyRepository;
import com.socialcoisa.rede.companies.domain.Company;
import com.socialcoisa.rede.companies.domain.CompanyMember;
import com.socialcoisa.rede.companies.domain.CompanyRole;

@Repository
class CompanyPersistenceAdapter implements CompanyRepository {

	private final SpringDataCompanyJpaRepository companies;
	private final SpringDataCompanyMemberJpaRepository members;

	CompanyPersistenceAdapter(SpringDataCompanyJpaRepository companies, SpringDataCompanyMemberJpaRepository members) {
		this.companies = companies;
		this.members = members;
	}

	@Override
	public Company save(Company company) {
		return companies.save(CompanyJpaEntity.fromDomain(company)).toDomain();
	}

	@Override
	public Optional<Company> findById(String id) {
		return companies.findById(id).map(CompanyJpaEntity::toDomain);
	}

	@Override
	public Optional<Company> findBySlug(String slug) {
		return companies.findBySlug(slug).map(CompanyJpaEntity::toDomain);
	}

	@Override
	public List<Company> findByMember(String userId, int limit) {
		return companies.findByMember(userId, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(CompanyJpaEntity::toDomain).toList();
	}

	@Override
	public boolean existsBySlug(String slug) {
		return companies.existsBySlug(slug);
	}

	@Override
	public List<Company> listAll(int limit) {
		return companies.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
			.map(CompanyJpaEntity::toDomain).toList();
	}

	@Override
	public CompanyMember saveMember(CompanyMember member) {
		return members.save(CompanyMemberJpaEntity.fromDomain(member)).toDomain();
	}

	@Override
	public Optional<CompanyMember> findMember(String companyId, String userId) {
		return members.findByCompanyIdAndUserId(companyId, userId).map(CompanyMemberJpaEntity::toDomain);
	}

	@Override
	public List<CompanyMember> membersOf(String companyId) {
		return members.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
			.map(CompanyMemberJpaEntity::toDomain).toList();
	}
}
