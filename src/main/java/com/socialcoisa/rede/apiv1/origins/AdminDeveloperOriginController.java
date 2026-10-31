package com.socialcoisa.rede.apiv1.origins;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Administração das origens de terceiros. O bloqueio automático já acontece sozinho; aqui o admin
 * vê o que foi derrubado e pode bloquear/liberar manualmente.
 *
 * Estas rotas ficam sob /admin e o gate de origem só aceita a origem confiável do frontend.
 */
@RestController
@RequestMapping("/admin/developers/origins")
class AdminDeveloperOriginController {

	private final DeveloperOriginService origins;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	AdminDeveloperOriginController(DeveloperOriginService origins, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard) {
		this.origins = origins;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	@GetMapping
	List<DeveloperOriginService.AdminOriginView> list(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "200") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return origins.listAll(limit);
	}

	@PostMapping("/{id}/block")
	DeveloperOriginService.AdminOriginView block(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody(required = false) BlockOriginRequest request) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		origins.blockById(id, request == null ? "Bloqueio manual da administracao" : request.reason());
		return origins.byId(id);
	}

	@PostMapping("/{id}/unblock")
	DeveloperOriginService.AdminOriginView unblock(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		origins.unblock(id);
		return origins.byId(id);
	}

	record BlockOriginRequest(String reason) {
	}
}
