package com.socialcoisa.rede.search.adapters.out.persistence;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.search.application.SearchService.SearchResult;
import com.socialcoisa.rede.search.application.port.out.SearchRepository;
import com.socialcoisa.rede.shared.web.LikePattern;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Repository
class SearchPersistenceAdapter implements SearchRepository {

	private final EntityManager em;

	SearchPersistenceAdapter(EntityManager em) {
		this.em = em;
	}

	@Override
	public List<SearchResult> searchUsers(String query, int limit) {
		Query q = em.createNativeQuery("""
				SELECT id, name, headline, avatar, username,
				  (CASE WHEN LOWER(name) = :exact THEN 10
				        WHEN LOWER(name) LIKE :prefix ESCAPE '!' THEN 5
				        WHEN LOWER(name) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(headline) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(username) = :exact THEN 8 ELSE 0 END) as relevance
				FROM users
				WHERE LOWER(name) LIKE :q ESCAPE '!' OR LOWER(username) LIKE :q ESCAPE '!' OR LOWER(headline) LIKE :q ESCAPE '!'
				ORDER BY relevance DESC, name
				LIMIT :limit""");
		bindTerm(q, query);
		q.setParameter("limit", limit);
		return mapUserResults(q.getResultList());
	}

	@Override
	public List<SearchResult> searchCommunities(String query, int limit) {
		Query q = em.createNativeQuery("""
				SELECT id, name, description, cover,
				  (CASE WHEN LOWER(name) = :exact THEN 10
				        WHEN LOWER(name) LIKE :prefix ESCAPE '!' THEN 5
				        WHEN LOWER(name) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(topic) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END) as relevance
				FROM communities
				WHERE LOWER(name) LIKE :q ESCAPE '!' OR LOWER(description) LIKE :q ESCAPE '!' OR LOWER(topic) LIKE :q ESCAPE '!'
				ORDER BY relevance DESC, name
				LIMIT :limit""");
		bindTerm(q, query);
		q.setParameter("limit", limit);
		return mapResults(q.getResultList(), "communities", "/api/communities/");
	}

	@Override
	public List<SearchResult> searchJobs(String query, int limit) {
		Query q = em.createNativeQuery("""
				SELECT id, title, company, logo,
				  (CASE WHEN LOWER(title) = :exact THEN 10
				        WHEN LOWER(title) LIKE :prefix ESCAPE '!' THEN 5
				        WHEN LOWER(title) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(company) LIKE :prefix ESCAPE '!' THEN 3
				          WHEN LOWER(company) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END) as relevance
				FROM jobs
				WHERE LOWER(title) LIKE :q ESCAPE '!' OR LOWER(company) LIKE :q ESCAPE '!'
				ORDER BY relevance DESC, title
				LIMIT :limit""");
		bindTerm(q, query);
		q.setParameter("limit", limit);
		return mapResults(q.getResultList(), "jobs", "/api/jobs/");
	}

	@Override
	public List<SearchResult> searchEvents(String query, int limit) {
		Query q = em.createNativeQuery("""
				SELECT id, title, host, cover,
				  (CASE WHEN LOWER(title) = :exact THEN 10
				        WHEN LOWER(title) LIKE :prefix ESCAPE '!' THEN 5
				        WHEN LOWER(title) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(host) LIKE :prefix ESCAPE '!' THEN 3
				          WHEN LOWER(host) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END) as relevance
				FROM events
				WHERE LOWER(title) LIKE :q ESCAPE '!' OR LOWER(host) LIKE :q ESCAPE '!'
				ORDER BY relevance DESC, title
				LIMIT :limit""");
		bindTerm(q, query);
		q.setParameter("limit", limit);
		return mapResults(q.getResultList(), "events", "/api/events/");
	}

	@Override
	public List<SearchResult> searchMentors(String query, int limit) {
		Query q = em.createNativeQuery("""
				SELECT m.id, u.name, u.headline, u.avatar,
				  (CASE WHEN LOWER(u.name) = :exact THEN 10
				        WHEN LOWER(u.name) LIKE :prefix ESCAPE '!' THEN 5
				        WHEN LOWER(u.name) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END
				   + CASE WHEN LOWER(m.expertise) LIKE :q ESCAPE '!' THEN 1 ELSE 0 END) as relevance
				FROM mentors m
				JOIN users u ON u.id = m.user_id
				WHERE LOWER(u.name) LIKE :q ESCAPE '!' OR LOWER(m.expertise) LIKE :q ESCAPE '!'
				ORDER BY relevance DESC, u.name
				LIMIT :limit""");
		bindTerm(q, query);
		q.setParameter("limit", limit);
		return mapResults(q.getResultList(), "mentors", "/api/mentors/");
	}

	/** `query` is the raw user term: wildcards are escaped here, never trusted from the caller. */
	private static void bindTerm(Query q, String query) {
		q.setParameter("q", LikePattern.contains(query));
		q.setParameter("prefix", LikePattern.startsWith(query));
		q.setParameter("exact", query.trim().toLowerCase(Locale.ROOT));
	}

	@SuppressWarnings("unchecked")
	private List<SearchResult> mapResults(List<Object[]> rows, String type, String hrefPrefix) {
		return rows.stream()
			.map(row -> new SearchResult(
				(String) row[0],
				type,
				(String) row[1],
				(String) row[2],
				(String) row[3],
				hrefPrefix + row[0]))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private List<SearchResult> mapUserResults(List<Object[]> rows) {
		return rows.stream()
			.map(row -> new SearchResult(
				(String) row[0],
				"users",
				(String) row[1],
				(String) row[2],
				(String) row[3],
				"/api/users/" + row[4]))
			.toList();
	}

}
