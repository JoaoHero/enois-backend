package com.socialcoisa.rede.importlinkedin.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactPreference;

public interface LinkedinContactPreferenceRepository {

	Optional<LinkedinContactPreference> findByUserId(String userId);

	LinkedinContactPreference save(LinkedinContactPreference preference);

	/** Usuários com o convite automático ligado (o worker percorre essa lista). */
	List<String> findUserIdsWithAutoInvite();
}