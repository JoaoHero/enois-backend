package com.socialcoisa.rede.contributors.application;

/** Dados que chegam do formulário público da landing. */
public record ContributorApplicationCommand(String name, String github, String email, String area,
		String experience, String message, boolean acceptedTerms) {
}
