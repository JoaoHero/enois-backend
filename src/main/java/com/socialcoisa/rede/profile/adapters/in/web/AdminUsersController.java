package com.socialcoisa.rede.profile.adapters.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.profile.application.AdminUserService;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Gestão do papel de administrador: um admin lista, promove e revoga outros admins. O papel vive
 * na coluna imutável (nunca vem de e-mail/username); esta é a única porta de mudança fora de uma
 * operação manual no banco.
 */
@RestController
class AdminUsersController {

	private final AdminUserService admins;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;

	AdminUsersController(AdminUserService admins, CurrentUserResolver currentUserResolver,
			AdminGuard adminGuard) {
		this.admins = admins;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
	}

	@GetMapping("/admin/admins")
	List<AdminUserService.AdminView> list(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		return admins.admins();
	}

	record PromoteRequest(String username) {
	}

	@PostMapping("/admin/admins")
	AdminUserService.AdminView promote(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody PromoteRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		return admins.promote(request.username(), user);
	}

	@PostMapping("/admin/admins/{username}/revoke")
	AdminUserService.AdminView revoke(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String username) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		adminGuard.requireAdmin(user);
		return admins.revoke(username, user);
	}
}
