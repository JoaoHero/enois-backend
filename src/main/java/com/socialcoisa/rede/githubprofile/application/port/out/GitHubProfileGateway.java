package com.socialcoisa.rede.githubprofile.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Leitura pública do perfil do GitHub para importar projetos e o README. Usa o token fine-grained
 * de leitura (quando existe) só para elevar o limite; nunca pede nem guarda token do usuário.
 */
public interface GitHubProfileGateway {

	List<GitHubUserRepository> listUserRepositories(String login, int maxPages);

	Optional<GitHubProfileReadme> profileReadme(String login);

	record GitHubUserRepository(String fullName, String name, String description, String htmlUrl, String homepage,
			String language, List<String> topics, int stars, int forks, boolean fork, boolean archived,
			Instant updatedAt, String defaultBranch) {
	}

	record GitHubProfileReadme(String markdown, String repoUrl, String defaultBranch) {
	}
}
