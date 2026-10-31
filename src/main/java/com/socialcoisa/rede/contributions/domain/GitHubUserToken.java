package com.socialcoisa.rede.contributions.domain;

import java.time.Instant;

/**
 * Token OAuth do GitHub de uma pessoa, cifrado em repouso. Só existe quando o vínculo pediu o
 * escopo de escrita (`repo`) e a chave de criptografia está configurada.
 */
public record GitHubUserToken(String userId, String tokenCipher, String scopes, Instant createdAt,
		Instant updatedAt) {
}