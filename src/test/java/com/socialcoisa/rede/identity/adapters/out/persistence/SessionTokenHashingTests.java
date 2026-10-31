package com.socialcoisa.rede.identity.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;

/** Credencial de sessão não fica em texto no banco; a migração mantém o token antigo funcionando. */
@SpringBootTest
class SessionTokenHashingTests {

	private static final String USER = "hash_test_user";

	@Autowired
	private AuthSessionRepository sessions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SessionTokenHashMigration migration;

	@BeforeEach
	void createUser() {
		Instant now = Instant.now();
		jdbc.update("delete from auth_sessions where user_id = ?", USER);
		jdbc.update("delete from users where id = ?", USER);
		jdbc.update("""
			insert into users (id, username, name, email, open_to_work, is_mentor, created_at, updated_at)
			values (?, ?, ?, ?, false, false, ?, ?)
			""", USER, USER, "Hash Test", USER + "@test.com", Timestamp.from(now), Timestamp.from(now));
	}

	@AfterEach
	void cleanup() {
		jdbc.update("delete from auth_sessions where user_id = ?", USER);
		jdbc.update("delete from users where id = ?", USER);
	}

	@Test
	void savesOnlyHashesAndStillLooksUpByRawToken() {
		Instant now = Instant.now();
		sessions.save("raw-access-1", "raw-refresh-1", USER, now, now.plusSeconds(3600),
			now.plusSeconds(86_400), "127.0.0.1");

		Map<String, Object> row = jdbc.queryForMap(
			"select token, refresh_token from auth_sessions where user_id = ?", USER);
		assertThat(row.get("token")).asString().matches("[0-9a-f]{64}").isNotEqualTo("raw-access-1");
		assertThat(row.get("refresh_token")).asString().matches("[0-9a-f]{64}")
			.isNotEqualTo("raw-refresh-1");

		assertThat(sessions.findActiveUserId("raw-access-1", now.plusSeconds(10))).contains(USER);
		assertThat(sessions.findActiveUserIdByRefreshToken("raw-refresh-1", now.plusSeconds(10)))
			.contains(USER);

		sessions.revoke("raw-access-1", now.plusSeconds(20));
		assertThat(sessions.findActiveUserId("raw-access-1", now.plusSeconds(30))).isEmpty();
	}

	@Test
	void aHashCopiedFromTheTableDoesNotAuthenticate() {
		Instant now = Instant.now();
		sessions.save("raw-access-2", "raw-refresh-2", USER, now, now.plusSeconds(3600),
			now.plusSeconds(86_400), "127.0.0.1");
		Map<String, Object> row = jdbc.queryForMap(
			"select token, refresh_token from auth_sessions where user_id = ?", USER);

		assertThat(sessions.findActiveUserId((String) row.get("token"), now.plusSeconds(10))).isEmpty();
		assertThat(sessions.findActiveUserIdByRefreshToken((String) row.get("refresh_token"), now.plusSeconds(10)))
			.isEmpty();
	}

	@Test
	void migratesLegacyRawSessionWithoutInvalidatingTheClient() {
		Instant now = Instant.now();
		jdbc.update("""
			insert into auth_sessions (token, refresh_token, user_id, created_at, expires_at, refresh_expires_at, ip)
			values (?, ?, ?, ?, ?, ?, ?)
			""", "legacy-access-1", "legacy-refresh-1", USER, Timestamp.from(now),
			Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now.plusSeconds(86_400)), "127.0.0.1");

		// Durante o rolling update, um pod antigo pode ter criado a sessão em claro: o fallback
		// mantém a pessoa autenticada até o rehash periódico.
		assertThat(sessions.findActiveUserId("legacy-access-1", now.plusSeconds(10))).contains(USER);

		migration.run(null);

		Map<String, Object> row = jdbc.queryForMap(
			"select token, refresh_token from auth_sessions where user_id = ?", USER);
		assertThat(row.get("token")).asString().matches("[0-9a-f]{64}").isNotEqualTo("legacy-access-1");
		assertThat(row.get("refresh_token")).asString().matches("[0-9a-f]{64}")
			.isNotEqualTo("legacy-refresh-1");
		assertThat(sessions.findActiveUserId("legacy-access-1", now.plusSeconds(10))).contains(USER);
	}
}
