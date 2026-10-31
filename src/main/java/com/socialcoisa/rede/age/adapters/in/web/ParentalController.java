package com.socialcoisa.rede.age.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.age.application.ParentalConsentService;
import com.socialcoisa.rede.age.application.ParentalConsentService.InviteView;
import com.socialcoisa.rede.age.application.ParentalConsentService.SupervisedMinorView;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.StatusResponse;

/**
 * Supervisao parental: o menor convida, o responsavel (adulto, autenticado) aceita/recusa, e o
 * vinculo pode ser revogado. O backend e a fonte de verdade — nada disso depende de flag do cliente.
 */
@RestController
@RequestMapping("/age/parental")
class ParentalController {

	private final ParentalConsentService parentalConsent;
	private final CurrentUserResolver currentUserResolver;

	ParentalController(ParentalConsentService parentalConsent, CurrentUserResolver currentUserResolver) {
		this.parentalConsent = parentalConsent;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping("/invite")
	InviteView invite(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody InviteRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return parentalConsent.invite(user.id().value(), request.email());
	}

	@PostMapping("/accept")
	StatusResponse accept(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody TokenRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		parentalConsent.accept(user.id().value(), request.token());
		return new StatusResponse(request.token(), "SUPERVISION_ACCEPTED");
	}

	@PostMapping("/reject")
	StatusResponse reject(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody TokenRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		parentalConsent.reject(user.id().value(), request.token());
		return new StatusResponse(request.token(), "SUPERVISION_REJECTED");
	}

	@PostMapping("/revoke")
	StatusResponse revoke(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody RevokeRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		parentalConsent.revoke(user.id().value(), request.minorUserId());
		return new StatusResponse(request.minorUserId(), "SUPERVISION_REVOKED");
	}

	/** Perfis que ESTE responsavel supervisiona (nunca os de outro). */
	@GetMapping("/mine")
	List<SupervisedMinorView> mine(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return parentalConsent.supervisedBy(user.id().value());
	}

	record InviteRequest(String email) {
	}

	record TokenRequest(String token) {
	}

	record RevokeRequest(String minorUserId) {
	}
}
