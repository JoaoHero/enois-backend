package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.games.domain.GameDistribution;
import com.socialcoisa.rede.games.domain.GameStage;
import com.socialcoisa.rede.games.domain.GameStatus;

interface SpringDataGameJpaRepository extends JpaRepository<GameJpaEntity, String> {

	Optional<GameJpaEntity> findByUserIdAndSlug(String userId, String slug);

	boolean existsByUserIdAndSlug(String userId, String slug);

	List<GameJpaEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

	List<GameJpaEntity> findByUserIdAndStatusOrderBySubmittedAtDesc(String userId, GameStatus status);

	List<GameJpaEntity> findByStatusOrderBySubmittedAtDesc(GameStatus status, Pageable pageable);

	List<GameJpaEntity> findByUserIdInAndStatusOrderByPublishedAtDesc(java.util.Collection<String> userIds,
			GameStatus status, Pageable pageable);

	List<GameJpaEntity> findByStatusOrderBySubmittedAtDesc(GameStatus status);

	/**
	 * Catálogo com busca e filtros. Cada filtro só vale quando o parâmetro não é nulo, e a ORDEM vem
	 * do `Pageable` (lista fechada no serviço) — nunca de texto vindo da URL.
	 */
	@Query("""
		SELECT g FROM GameJpaEntity g
		WHERE g.status = :status
		  AND (:query IS NULL
		       OR LOWER(g.title) LIKE :query ESCAPE '!'
		       OR LOWER(COALESCE(g.description, '')) LIKE :query ESCAPE '!'
		       OR LOWER(COALESCE(g.tags, '')) LIKE :query ESCAPE '!')
		  AND (:genre IS NULL OR LOWER(COALESCE(g.genres, '')) LIKE :genre ESCAPE '!')
		  AND (:tag IS NULL OR LOWER(COALESCE(g.tags, '')) LIKE :tag ESCAPE '!')
		  AND (:stage IS NULL OR g.stage = :stage)
		  AND (:distribution IS NULL OR g.distribution = :distribution)
		""")
	Slice<GameJpaEntity> search(@Param("status") GameStatus status, @Param("query") String query,
			@Param("genre") String genre, @Param("tag") String tag, @Param("stage") GameStage stage,
			@Param("distribution") GameDistribution distribution, Pageable pageable);

	/** Quantos jogos publicados por criador (a lista de criadores do catálogo não faz N+1). */
	@Query("SELECT g.userId, COUNT(g) FROM GameJpaEntity g WHERE g.status = :status GROUP BY g.userId")
	List<Object[]> countByUser(@Param("status") GameStatus status);
}
