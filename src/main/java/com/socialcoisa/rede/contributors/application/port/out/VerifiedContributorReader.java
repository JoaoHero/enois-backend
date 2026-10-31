package com.socialcoisa.rede.contributors.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Quem já tem contribuição verificada pelo GitHub (selo ativo). O painel de contribuintes usa esta
 * leitura para listar a pessoa automaticamente; a confirmação do formulário não passa por revisão.
 */
public interface VerifiedContributorReader {

	Optional<VerifiedContributor> find(String userId);

	List<VerifiedContributor> list();

	record VerifiedContributor(String userId, String username, String displayName, String githubLogin,
			String sealName, int evidenceCount, Instant grantedAt) {
	}
}
