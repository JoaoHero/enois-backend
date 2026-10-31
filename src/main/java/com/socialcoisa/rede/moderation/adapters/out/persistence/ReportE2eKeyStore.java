package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Clock;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportE2eKeyStore {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	ReportE2eKeyStore(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public void save(String reportId, String conversationKey) {
		try {
			jdbc.update("insert into report_e2e_keys (report_id, conversation_key, created_at) values (?, ?, ?)",
				reportId, conversationKey, java.sql.Timestamp.from(clock.instant()));
		}
		catch (org.springframework.dao.DuplicateKeyException alreadySaved) {
			// Idempotência: a chave já foi salva numa execução anterior.
		}
	}

	public Optional<String> findByReportId(String reportId) {
		var results = jdbc.queryForList(
			"select conversation_key from report_e2e_keys where report_id = ?", String.class, reportId);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}
}
