package com.socialcoisa.rede.contributors.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;

public interface ContributorApplicationRepository {

	ContributorApplication save(ContributorApplication application);

	List<ContributorApplication> list(ContributorApplicationStatus status, int limit);

	Optional<ContributorApplication> findById(String id);

	Optional<ContributorApplication> findByUserId(String userId);

	/** Candidatura pública (sem conta) que usou o mesmo handle; é reaproveitada na confirmação. */
	Optional<ContributorApplication> findLandingByGithub(String github);
}
