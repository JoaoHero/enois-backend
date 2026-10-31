package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.companies.domain.MicroProjectStatus;

interface SpringDataMicroProjectJpaRepository extends JpaRepository<MicroProjectJpaEntity, String> {

	List<MicroProjectJpaEntity> findByStatusOrderByCreatedAtDesc(MicroProjectStatus status, Pageable pageable);

	List<MicroProjectJpaEntity> findTop50ByCompanyIdOrderByCreatedAtDesc(String companyId);

	long countByCompanyIdAndStatus(String companyId, MicroProjectStatus status);

	@org.springframework.data.jpa.repository.Query("""
			SELECT p.companyId, COUNT(p) FROM MicroProjectJpaEntity p
			WHERE p.status = com.socialcoisa.rede.companies.domain.MicroProjectStatus.OPEN AND p.companyId IN :ids
			GROUP BY p.companyId""")
	List<Object[]> countOpenByCompanyIds(
			@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);
}
