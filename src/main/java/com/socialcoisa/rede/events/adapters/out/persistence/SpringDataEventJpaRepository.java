package com.socialcoisa.rede.events.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEventJpaRepository extends JpaRepository<EventJpaEntity, String> {

	List<EventJpaEntity> findBySanitizedAtIsNull();

	List<EventJpaEntity> findBySanitizedAtBefore(java.time.Instant threshold);


	@Query("SELECT e FROM EventJpaEntity e WHERE (:before IS NULL OR e.createdAt < :before)"
		+ " AND (:recurringOnly = false OR e.recurrence <> 'NONE') ORDER BY e.createdAt DESC")
	List<EventJpaEntity> findAllBefore(@Param("before") Instant before, @Param("recurringOnly") boolean recurringOnly,
			Pageable pageable);

	@Query("SELECT e FROM EventJpaEntity e WHERE e.format = :format AND (:before IS NULL OR e.createdAt < :before)"
		+ " AND (:recurringOnly = false OR e.recurrence <> 'NONE') ORDER BY e.createdAt DESC")
	List<EventJpaEntity> findByFormat(@Param("format") String format, @Param("before") Instant before,
			@Param("recurringOnly") boolean recurringOnly, Pageable pageable);

	List<EventJpaEntity> findByRecurrenceNot(String recurrence);

	java.util.Optional<EventJpaEntity> findFirstBySeriesIdOrderByCreatedAtDesc(String seriesId);

	boolean existsBySeriesIdAndDate(String seriesId, String date);

	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE EventJpaEntity e SET e.recurrence = :recurrence WHERE e.seriesId = :seriesId")
	int updateSeriesRecurrence(@Param("seriesId") String seriesId, @Param("recurrence") String recurrence);

}
