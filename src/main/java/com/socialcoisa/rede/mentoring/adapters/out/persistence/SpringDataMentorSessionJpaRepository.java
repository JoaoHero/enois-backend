package com.socialcoisa.rede.mentoring.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMentorSessionJpaRepository extends JpaRepository<MentorSessionJpaEntity, String> {

	List<MentorSessionJpaEntity> findByMentorIdOrderByCreatedAtDesc(String mentorId);

	List<MentorSessionJpaEntity> findByMenteeIdOrderByCreatedAtDesc(String menteeId);

	/** Sessões pagas ainda em aberto: a liberação automática depois da janela de contestação. */
	List<MentorSessionJpaEntity> findByStatusAndPriceSparksIsNotNull(com.socialcoisa.rede.mentoring.domain.SessionStatus status);

}
