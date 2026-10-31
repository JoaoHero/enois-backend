package com.socialcoisa.rede.linkedin.adapters.in.web;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.linkedin.application.LinkedInIntegrationService;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

@RestController
@RequestMapping("/me/integrations/linkedin")
class LinkedInIntegrationController {

	private final CurrentUserResolver currentUserResolver;
	private final LinkedInIntegrationService linkedin;

	LinkedInIntegrationController(CurrentUserResolver currentUserResolver, LinkedInIntegrationService linkedin) {
		this.currentUserResolver = currentUserResolver;
		this.linkedin = linkedin;
	}

	@GetMapping
	StatusView status(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return StatusView.from(linkedin.status(user.id().value()));
	}

	@PutMapping
	StatusView setPostSync(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody UpdateRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		if (request.postSync() == null) {
			throw new ApiException(400, "BAD_REQUEST", "Informe a preferencia de sincronizacao.");
		}
		return StatusView.from(linkedin.setPostSync(user.id().value(), request.postSync()));
	}

	record UpdateRequest(Boolean postSync) {
	}

	record StatusView(boolean connected, boolean postSync, boolean canPost, Instant tokenExpiresAt,
			String reason, String lastShareStatus, Instant lastShareAt) {
		static StatusView from(LinkedInIntegrationService.Status status) {
			return new StatusView(status.connected(), status.postSync(), status.canPost(),
				status.tokenExpiresAt(), status.reason(), status.lastShareStatus(), status.lastShareAt());
		}
	}
}
