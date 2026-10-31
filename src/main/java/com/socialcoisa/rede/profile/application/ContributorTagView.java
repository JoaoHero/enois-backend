package com.socialcoisa.rede.profile.application;

import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Tag "Contribuinte" que acompanha o nome de quem tem selo ativo. Só existe na projeção quando a
 * pessoa é contribuinte; a cor é a escolhida no perfil (nulo = cor padrão do tema).
 */
public record ContributorTagView(String color) {

	public static ContributorTagView from(UserProfile profile) {
		return profile == null ? null : new ContributorTagView(profile.contributorTagColor());
	}
}
