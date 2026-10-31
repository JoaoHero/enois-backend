package com.socialcoisa.rede.shared.web;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.ModeratorService;

@Component
public class ModeratorGuard {

	private final AdminGuard adminGuard;
	private final ModeratorService moderators;

	public ModeratorGuard(AdminGuard adminGuard, ModeratorService moderators) {
		this.adminGuard = adminGuard;
		this.moderators = moderators;
	}

	public void requireModerator(CurrentUser user) {
		if (!isModerator(user)) {
			throw new ApiException(403, "FORBIDDEN", "Acesso restrito a moderadores.");
		}
	}

	public boolean isModerator(CurrentUser user) {
		return adminGuard.isAdmin(user) || moderators.isModerator(user.id().value());
	}
}
