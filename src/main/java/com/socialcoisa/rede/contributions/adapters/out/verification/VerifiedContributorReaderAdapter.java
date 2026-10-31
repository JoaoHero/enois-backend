package com.socialcoisa.rede.contributions.adapters.out.verification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.contributions.application.port.out.GitHubIdentityRepository;
import com.socialcoisa.rede.contributions.application.port.out.UserSealRepository;
import com.socialcoisa.rede.contributions.domain.GitHubIdentity;
import com.socialcoisa.rede.contributions.domain.UserSeal;
import com.socialcoisa.rede.contributors.application.port.out.VerifiedContributorReader;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Liga o painel de contribuintes às evidências verificadas: só entra quem tem selo ativo e GitHub
 * vinculado. Nome/e-mail exibidos vêm do perfil; o handle vem do vínculo, nunca do formulário.
 */
@Component
class VerifiedContributorReaderAdapter implements VerifiedContributorReader {

	private final UserSealRepository seals;
	private final GitHubIdentityRepository identities;
	private final UserProfileRepository profiles;

	VerifiedContributorReaderAdapter(UserSealRepository seals, GitHubIdentityRepository identities,
			UserProfileRepository profiles) {
		this.seals = seals;
		this.identities = identities;
		this.profiles = profiles;
	}

	@Override
	public Optional<VerifiedContributor> find(String userId) {
		List<UserSeal> active = seals.findActiveByUser(userId);
		if (active.isEmpty()) {
			return Optional.empty();
		}
		return identities.findByUserId(userId).map(identity -> view(userId, active, identity));
	}

	@Override
	public List<VerifiedContributor> list() {
		Map<String, List<UserSeal>> byUser = new LinkedHashMap<>();
		for (UserSeal seal : seals.findAllActive()) {
			byUser.computeIfAbsent(seal.userId(), key -> new ArrayList<>()).add(seal);
		}
		List<VerifiedContributor> result = new ArrayList<>();
		for (var entry : byUser.entrySet()) {
			identities.findByUserId(entry.getKey())
				.ifPresent(identity -> result.add(view(entry.getKey(), entry.getValue(), identity)));
		}
		return result;
	}

	private VerifiedContributor view(String userId, List<UserSeal> active, GitHubIdentity identity) {
		List<UserSeal> ordered = active.stream()
			.sorted(Comparator.comparingInt(seal -> seal.category().ordinal()))
			.toList();
		String sealName = ordered.stream().map(seal -> seal.sealType().displayName()).distinct()
			.collect(Collectors.joining(" · "));
		int evidence = ordered.stream().mapToInt(UserSeal::evidenceCount).sum();
		Instant grantedAt = ordered.stream().map(UserSeal::grantedAt).max(Comparator.naturalOrder())
			.orElse(null);
		Optional<UserProfile> profile = profiles.findById(userId);
		String displayName = profile.map(UserProfile::name)
			.filter(StringUtils::hasText)
			.orElseGet(() -> profile.map(UserProfile::username).orElse(identity.githubLogin()));
		return new VerifiedContributor(userId, profile.map(UserProfile::username).orElse(null),
			displayName, identity.githubLogin(), sealName, evidence, grantedAt);
	}
}
