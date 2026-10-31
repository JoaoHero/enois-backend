package com.socialcoisa.rede.contributors.domain;

/** De onde veio a candidatura e, portanto, quem decide: a triagem humana ou a verificação do GitHub. */
public enum ContributorApplicationSource {
	/** Formulário público da landing; passa pela fila manual. */
	LANDING,
	/** Conta com contribuição verificada; o próprio GitHub aprova, sem revisão manual. */
	GITHUB_VERIFIED
}
