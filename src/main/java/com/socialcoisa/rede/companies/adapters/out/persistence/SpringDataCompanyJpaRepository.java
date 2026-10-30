package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataCompanyJpaRepository extends JpaRepository<CompanyJpaEntity, String> {

	Optional<CompanyJpaEntity> findBySlug(String slug);

	boolean existsBySlug(String slug);

	@Query("""
			SELECT c FROM CompanyJpaEntity c
			WHERE c.id IN (SELECT m.companyId FROM CompanyMemberJpaEntity m WHERE m.userId = :userId)
			ORDER BY c.createdAt DESC""")
	List<CompanyJpaEntity> findByMember(@Param("userId") String userId, Pageable pageable);

	List<CompanyJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
