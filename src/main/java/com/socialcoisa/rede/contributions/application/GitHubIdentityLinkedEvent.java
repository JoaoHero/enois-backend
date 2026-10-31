package com.socialcoisa.rede.contributions.application;

/** Vínculo GitHub concluído: dispara a verificação das contribuições daquele perfil. */
public record GitHubIdentityLinkedEvent(String userId, String githubUserId) {
}
