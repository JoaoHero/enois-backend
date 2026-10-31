package com.socialcoisa.rede.contributions.application;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.contributions.application.port.out.GitHubUserTokenRepository;
import com.socialcoisa.rede.contributions.domain.GitHubUserToken;
import com.socialcoisa.rede.shared.security.SecretCipher;

/**
 * Token de escrita do GitHub por pessoa.
 *
 * <p>Só é guardado quando o vínculo pediu o escopo `repo` (necessário para o repositório privado de
 * suporte) e a chave de criptografia está configurada. Sem token da pessoa, as ações que exigem
 * escrita caem no token de serviço — com atribuição explícita — ou falham com erro claro; token em
 * claro nunca é gravado nem devolvido.</p>
 */
@Service
public class GitHubUserTokenService {

	private static final Logger LOG = LoggerFactory.getLogger(GitHubUserTokenService.class);

	private final GitHubUserTokenRepository repository;
	private final SecretCipher cipher;
	private final Clock clock;

	public GitHubUserTokenService(GitHubUserTokenRepository repository, SecretCipher cipher, Clock clock) {
		this.repository = repository;
		this.cipher = cipher;
		this.clock = clock;
	}

	/** Guarda o token quando o consentimento inclui escrita; qualquer outro escopo é ignorado. */
	@Transactional
	public void remember(String userId, String accessToken, String scopes) {
		if (userId == null || userId.isBlank() || accessToken == null || accessToken.isBlank()) {
			return;
		}
		if (!hasWriteScope(scopes)) {
			return;
		}
		if (!cipher.isEnabled()) {
			LOG.info("Token de escrita do GitHub nao guardado: REDE_SECURITY_ENCRYPTION_KEY ausente.");
			return;
		}
		var existing = repository.findByUserId(userId);
		var now = clock.instant();
		repository.save(new GitHubUserToken(userId, cipher.encrypt(accessToken), scopes,
			existing.map(GitHubUserToken::createdAt).orElse(now), now));
	}

	public Optional<String> accessToken(String userId) {
		if (userId == null || userId.isBlank() || !cipher.isEnabled()) {
			return Optional.empty();
		}
		return repository.findByUserId(userId).flatMap(token -> {
			try {
				return Optional.of(cipher.decrypt(token.tokenCipher()));
			}
			catch (RuntimeException failure) {
				LOG.warn("Nao foi possivel decifrar o token GitHub de {}: {}", userId,
					failure.getClass().getSimpleName());
				return Optional.empty();
			}
		});
	}

	public boolean hasWriteToken(String userId) {
		return accessToken(userId).isPresent();
	}

	static boolean hasWriteScope(String scopes) {
		if (scopes == null || scopes.isBlank()) {
			return false;
		}
		for (String scope : scopes.toLowerCase(Locale.ROOT).split("[,\\s]+")) {
			if ("repo".equals(scope) || "public_repo".equals(scope)) {
				return true;
			}
		}
		return false;
	}
}