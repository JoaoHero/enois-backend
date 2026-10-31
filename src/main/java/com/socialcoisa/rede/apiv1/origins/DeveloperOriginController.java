package com.socialcoisa.rede.apiv1.origins;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Origens que o desenvolvedor cadastrou para chamar a API pública pelo navegador. Exige sessão:
 * a origem é vinculada à conta e o CORS só libera o que está aqui (ou as origens do app).
 */
@RestController
@RequestMapping("/developers/origins")
class DeveloperOriginController {

	private final DeveloperOriginService origins;
	private final CurrentUserResolver currentUserResolver;

	DeveloperOriginController(DeveloperOriginService origins, CurrentUserResolver currentUserResolver) {
		this.origins = origins;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping
	List<DeveloperOriginService.OriginView> list(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return origins.list(user.id().value());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	DeveloperOriginService.OriginView add(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody OriginRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return origins.add(user.id().value(), request == null ? null : request.origin());
	}

	@DeleteMapping("/{id}")
	void remove(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		origins.remove(user.id().value(), id);
	}

	record OriginRequest(String origin) {
	}
}
