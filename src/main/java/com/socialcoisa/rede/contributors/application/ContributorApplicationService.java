package com.socialcoisa.rede.contributors.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.contributors.application.port.out.ContributorApplicationRepository;
import com.socialcoisa.rede.contributors.application.port.out.VerifiedContributorReader;
import com.socialcoisa.rede.contributors.application.port.out.VerifiedContributorReader.VerifiedContributor;
import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.contributors.domain.ContributorExperience;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Fila de contribuidores do projeto open source.
 *
 * <p>A landing é pública, então a validação é conservadora. Quem já tem contribuição verificada
 * pelo GitHub entra automaticamente no painel e usa o mesmo formulário só para confirmar os dados:
 * a candidatura nasce APPROVED (origem GITHUB_VERIFIED) e não passa por revisão manual. O handle
 * do GitHub vem do vínculo verificado, nunca do texto enviado pelo cliente.</p>
 */
@Service
public class ContributorApplicationService {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern GITHUB = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");
	private static final Set<String> AREAS = Set.of("ENGENHARIA", "DESIGN", "PRODUTO", "DADOS", "INFRA",
		"DOCUMENTACAO", "COMUNIDADE", "OUTRO");
	private static final int MAX_LIMIT = 200;

	private final ContributorApplicationRepository repository;
	private final VerifiedContributorReader verified;
	private final UserProfileRepository profiles;
	private final Clock clock;

	public ContributorApplicationService(ContributorApplicationRepository repository,
			VerifiedContributorReader verified, UserProfileRepository profiles, Clock clock) {
		this.repository = repository;
		this.verified = verified;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Transactional
	public ContributorApplication apply(ContributorApplicationCommand command) {
		ContributorApplication application = ContributorApplication.createLanding(
			"contrib_" + UUID.randomUUID().toString().replace("-", ""), name(command), github(command), email(command),
			area(command), experience(command), message(command), clock.instant());
		return repository.save(application);
	}

	/**
	 * Confirmação de quem já tem contribuição verificada. Idempotente: atualiza a candidatura da
	 * própria conta (ou reaproveita a candidatura pública com o mesmo handle) em vez de duplicar.
	 */
	@Transactional
	public ContributorApplication applyVerified(String userId, ContributorApplicationCommand command) {
		VerifiedContributor contributor = verified.find(userId)
			.orElseThrow(() -> new ApiException(403, "CONTRIBUTOR_NOT_VERIFIED",
				"Este perfil ainda não tem contribuição verificada pelo GitHub."));
		String name = name(command);
		String email = email(command);
		String area = area(command);
		ContributorExperience experience = experience(command);
		String message = message(command);
		String github = contributor.githubLogin().toLowerCase(Locale.ROOT);
		Instant now = clock.instant();

		var existing = repository.findByUserId(userId)
			.or(() -> repository.findLandingByGithub(github));
		ContributorApplication application = existing
			.map(item -> item.withVerifiedData(userId, name, github, email, area, experience, message, now))
			.orElseGet(() -> ContributorApplication.createVerified(
				"contrib_" + UUID.randomUUID().toString().replace("-", ""), userId, name, github, email, area,
				experience, message, now));
		return repository.save(application);
	}

	public UserStatus statusForUser(String userId) {
		var contributor = verified.find(userId);
		var application = repository.findByUserId(userId);
		var profile = profiles.findById(userId);
		return new UserStatus(contributor.isPresent(), contributor.map(VerifiedContributor::githubLogin).orElse(null),
			contributor.isPresent() && application.isEmpty(), profile.map(item -> item.name()).orElse(null),
			profile.map(item -> item.email()).orElse(null), application.orElse(null));
	}

	/**
	 * Painel de contribuintes: candidaturas (landing + confirmações) e, automaticamente, quem tem
	 * contribuição verificada e ainda não confirmou os dados.
	 */
	public List<PanelEntry> panel() {
		List<ContributorApplication> applications = repository.list(null, MAX_LIMIT);
		List<VerifiedContributor> contributors = verified.list();
		Map<String, VerifiedContributor> byUser = new LinkedHashMap<>();
		for (VerifiedContributor contributor : contributors) {
			byUser.put(contributor.userId(), contributor);
		}
		Set<String> withApplication = new java.util.HashSet<>();
		List<PanelEntry> entries = new ArrayList<>();
		for (ContributorApplication application : applications) {
			if (application.userId() != null) {
				withApplication.add(application.userId());
			}
			entries.add(new PanelEntry(application, byUser.get(application.userId()), false));
		}
		for (VerifiedContributor contributor : contributors) {
			if (!withApplication.contains(contributor.userId())) {
				entries.add(new PanelEntry(null, contributor, true));
			}
		}
		entries.sort(Comparator.comparing(this::sortInstant, Comparator.nullsLast(Comparator.reverseOrder())));
		return entries.stream().limit(MAX_LIMIT).toList();
	}

	private Instant sortInstant(PanelEntry entry) {
		return entry.application() != null ? entry.application().createdAt() : entry.verified().grantedAt();
	}

	public List<ContributorApplication> list(ContributorApplicationStatus status, int limit) {
		return repository.list(status, Math.max(1, Math.min(limit, MAX_LIMIT)));
	}

	@Transactional
	public ContributorApplication updateStatus(String id, ContributorApplicationStatus status) {
		if (status == null) {
			throw new ApiException(400, "INVALID_STATUS", "Informe o novo status da candidatura.");
		}
		ContributorApplication application = repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "CONTRIBUTOR_NOT_FOUND", "Candidatura não encontrada."));
		return repository.save(application.withStatus(status));
	}

	private static String name(ContributorApplicationCommand command) {
		requireTerms(command);
		return required(command.name(), 2, 120, "INVALID_NAME", "Informe seu nome (2 a 120 caracteres).");
	}

	private static String github(ContributorApplicationCommand command) {
		requireTerms(command);
		String raw = clean(command.github());
		if (raw == null) {
			throw new ApiException(400, "INVALID_GITHUB", "Informe seu usuário do GitHub.");
		}
		String handle = raw;
		int githubIndex = raw.toLowerCase(Locale.ROOT).indexOf("github.com/");
		if (githubIndex >= 0) {
			handle = raw.substring(githubIndex + "github.com/".length());
		}
		handle = handle.replaceFirst("^@", "").replaceAll("/+$", "");
		handle = handle.toLowerCase(Locale.ROOT);
		if (!GITHUB.matcher(handle).matches()) {
			throw new ApiException(400, "INVALID_GITHUB", "Usuário do GitHub inválido.");
		}
		return handle;
	}

	private static String email(ContributorApplicationCommand command) {
		requireTerms(command);
		String clean = clean(command.email());
		if (clean == null || clean.length() > 180 || !EMAIL.matcher(clean).matches()) {
			throw new ApiException(400, "INVALID_EMAIL", "Informe um e-mail válido.");
		}
		return clean.toLowerCase(Locale.ROOT);
	}

	private static String area(ContributorApplicationCommand command) {
		requireTerms(command);
		String clean = clean(command.area());
		if (clean == null) {
			return null;
		}
		String normalized = clean.toUpperCase(Locale.ROOT);
		if (!AREAS.contains(normalized)) {
			throw new ApiException(400, "INVALID_AREA", "Área de contribuição inválida.");
		}
		return normalized;
	}

	private static ContributorExperience experience(ContributorApplicationCommand command) {
		requireTerms(command);
		String clean = clean(command.experience());
		if (clean == null) {
			return null;
		}
		try {
			return ContributorExperience.valueOf(clean.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException invalid) {
			throw new ApiException(400, "INVALID_EXPERIENCE", "Experiência inválida.");
		}
	}

	private static String message(ContributorApplicationCommand command) {
		requireTerms(command);
		return optional(command.message(), 2000, "INVALID_MESSAGE", "A mensagem passa de 2000 caracteres.");
	}

	private static void requireTerms(ContributorApplicationCommand command) {
		if (command == null || !command.acceptedTerms()) {
			throw new ApiException(400, "TERMS_NOT_ACCEPTED",
				"Para contribuir, aceite as regras de contribuição e os termos de uso.");
		}
	}

	private static String required(String value, int min, int max, String code, String message) {
		String clean = clean(value);
		if (clean == null || clean.length() < min) {
			throw new ApiException(400, code, message);
		}
		if (clean.length() > max) {
			throw new ApiException(400, code, message);
		}
		return clean;
	}

	private static String optional(String value, int max, String code, String message) {
		String clean = clean(value);
		if (clean != null && clean.length() > max) {
			throw new ApiException(400, code, message);
		}
		return clean;
	}

	private static String clean(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String clean = value.replaceAll("\\p{Cntrl}", "").trim();
		return clean.isEmpty() ? null : clean;
	}

	public record PanelEntry(ContributorApplication application, VerifiedContributor verified,
			boolean formPending) {
	}

	public record UserStatus(boolean verified, String github, boolean applicationPending, String name,
			String email, ContributorApplication application) {
	}
}
