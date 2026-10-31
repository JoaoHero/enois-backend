package com.socialcoisa.rede.importlinkedin.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataLinkedinContactPreferenceJpaRepository
		extends JpaRepository<LinkedinContactPreferenceJpaEntity, String> {

	@Query("select p.userId from LinkedinContactPreferenceJpaEntity p where p.autoInvite = true")
	List<String> findUserIdsWithAutoInvite();
}