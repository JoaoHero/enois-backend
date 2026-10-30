package com.socialcoisa.rede.companies.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.companies.domain.Company;
import com.socialcoisa.rede.companies.domain.CompanyMember;

public interface CompanyRepository {

	Company save(Company company);

	Optional<Company> findById(String id);

	Optional<Company> findBySlug(String slug);

	/** Pages the user is part of, in any role (the owner is a member too), newest first. */
	List<Company> findByMember(String userId, int limit);

	boolean existsBySlug(String slug);

	List<Company> listAll(int limit);

	CompanyMember saveMember(CompanyMember member);

	Optional<CompanyMember> findMember(String companyId, String userId);

	List<CompanyMember> membersOf(String companyId);
}
