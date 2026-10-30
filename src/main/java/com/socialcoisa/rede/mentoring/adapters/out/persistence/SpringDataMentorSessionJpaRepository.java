package com.socialcoisa.rede.mentoring.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMentorSessionJpaRepository extends JpaRepository<MentorSessionJpaEntity, String> {

	List<MentorSessionJpaEntity> findByMentorIdOrderByCreatedAtDesc(String mentorId);

}
