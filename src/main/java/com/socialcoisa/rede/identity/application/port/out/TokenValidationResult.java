package com.socialcoisa.rede.identity.application.port.out;

/**
 * Dados confiaveis extraidos do id_token depois de conferir assinatura, emissor, audience, validade
 * e nonce.
 *
 * @param externalUserId identificador do usuario no provedor (claim `sub`)
 * @param email          e-mail informado pelo provedor (pode vir vazio em logins Apple seguintes)
 * @param name           nome informado pelo provedor (idem)
 * @param emailVerified  o provedor afirma que o e-mail e verificado — nunca inferido do que o
 *                       cliente mandou
 * @param accessToken    token de acesso do provedor, quando o fluxo devolve um (LinkedIn usa para
 *                       publicar com w_member_social); nunca viaja para o cliente
 * @param accessTokenExpiresAt validade do accessToken (null quando nao ha token)
 * @param scope          escopos concedidos pelo provedor (GitHub devolve na troca do token)
 */
public record TokenValidationResult(String externalUserId, String email, String name, boolean emailVerified,
		String accessToken, java.time.Instant accessTokenExpiresAt, String scope) {

	public TokenValidationResult(String externalUserId, String email, String name, boolean emailVerified) {
		this(externalUserId, email, name, emailVerified, null, null, null);
	}

	public TokenValidationResult(String externalUserId, String email, String name, boolean emailVerified,
			String accessToken, java.time.Instant accessTokenExpiresAt) {
		this(externalUserId, email, name, emailVerified, accessToken, accessTokenExpiresAt, null);
	}

	public TokenValidationResult(String externalUserId, String email, String name) {
		this(externalUserId, email, name, true, null, null, null);
	}

	public TokenValidationResult {
		if (externalUserId == null || externalUserId.isBlank()) {
			throw new IllegalArgumentException("externalUserId is required");
		}
	}

	public String email() {
		return email == null ? "" : email;
	}

	public String name() {
		return name == null ? "" : name;
	}
}
