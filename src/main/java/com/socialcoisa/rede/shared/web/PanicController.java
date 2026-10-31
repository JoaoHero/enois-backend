package com.socialcoisa.rede.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.shared.security.PanicModeService;

/**
 * Botão de pânico do painel: liga/desliga o bloqueio global de escritas. Só admin. O endpoint
 * fica FORA do bloqueio do próprio pânico, senão não haveria como desligar.
 */
@RestController
class PanicController {

	private final PanicModeService panic;
	private final AdminGuard adminGuard;
	private final CurrentUserResolver currentUserResolver;

	PanicController(PanicModeService panic, AdminGuard adminGuard, CurrentUserResolver currentUserResolver) {
		this.panic = panic;
		this.adminGuard = adminGuard;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping("/admin/panic")
	PanicView status(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		requireAdmin(sessionToken);
		return view();
	}

	@PostMapping("/admin/panic")
	PanicView toggle(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody PanicRequest request) {
		requireAdmin(sessionToken);
		panic.setActive(request != null && Boolean.TRUE.equals(request.active()));
		return view();
	}

	private void requireAdmin(String sessionToken) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
	}

	private PanicView view() {
		boolean active = panic.active();
		boolean envLocked = panic.envLocked();
		return new PanicView(active, envLocked, envLocked
			? "Modo pânico ATIVADO pela variável de ambiente PANIC_MODE: o painel não consegue desligar."
			: active ? "Modo pânico ativo: toda escrita está respondendo 429."
				: "Modo pânico desligado: a aplicação está aceitando escritas.");
	}

	record PanicRequest(Boolean active) {
	}

	record PanicView(boolean active, boolean envLocked, String message) {
	}
}
