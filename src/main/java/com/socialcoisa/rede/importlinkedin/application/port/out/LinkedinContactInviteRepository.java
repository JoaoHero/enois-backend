package com.socialcoisa.rede.importlinkedin.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.importlinkedin.domain.LinkedinContactInvite;
import com.socialcoisa.rede.importlinkedin.domain.LinkedinInviteStatus;

public interface LinkedinContactInviteRepository {

	Optional<LinkedinContactInvite> findByUserAndKey(String userId, String nameKey);

	List<LinkedinContactInvite> findQueuedByUser(String userId, int limit);

	List<LinkedinContactInvite> findByUser(String userId, int limit);

	int countByUser(String userId);

	int countByUserAndStatus(String userId, LinkedinInviteStatus status);

	LinkedinContactInvite save(LinkedinContactInvite invite);
}