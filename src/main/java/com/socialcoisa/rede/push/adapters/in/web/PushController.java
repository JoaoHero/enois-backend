package com.socialcoisa.rede.push.adapters.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.push.application.PushService;
import com.socialcoisa.rede.push.domain.PushSubscription;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Contratos de Web Push: o app consulta se o push esta ativo e registra/remove o dispositivo
 * para receber as notificacoes tambem no celular.
 */
@RestController
class PushController {

	private final PushService pushService;
	private final CurrentUserResolver currentUserResolver;

	PushController(PushService pushService, CurrentUserResolver currentUserResolver) {
		this.pushService = pushService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/push/config")
	PushConfigView config() {
		return new PushConfigView(pushService.isEnabled(), pushService.publicKey());
	}

	@PostMapping("/push/subscriptions")
	ResponseEntity<SubscriptionView> subscribe(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody SubscriptionRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		PushSubscription subscription = pushService.register(user.id().value(), request.endpoint(),
			request.keys() != null ? request.keys().p256dh() : null, request.keys() != null ? request.keys().auth() : null);
		return ResponseEntity.status(HttpStatus.CREATED).body(new SubscriptionView(subscription.id()));
	}

	@DeleteMapping("/push/subscriptions")
	ResponseEntity<Void> unsubscribe(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(name = "endpoint") String endpoint) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		pushService.unregister(user.id().value(), endpoint);
		return ResponseEntity.noContent().build();
	}

	record PushConfigView(boolean enabled, String publicKey) {
	}

	record SubscriptionRequest(String endpoint, Keys keys) {
		record Keys(String p256dh, String auth) {
		}
	}

	record SubscriptionView(String id) {
	}
}
