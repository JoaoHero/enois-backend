package com.socialcoisa.rede.profile.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.notifications.application.NotificationService;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;

/**
 * Administração do papel de administrador: um admin lista, promove e revoga outros admins.
 *
 * O papel vive na coluna imutável (não vem de e-mail/username mutáveis) — aqui é a única porta
 * de mudança fora de uma operação manual no banco. Duas travas: a revisão é registrada (quem
 * promoveu, quando) e ninguém revoga a própria conta pela API (revogar a si mesmo é operação
 * manual, para não existir caminho acidental de travar a administração fora).
 */
@Service
public class AdminUserService {

	private final UserProfileRepository profiles;
	private final NotificationService notifications;

	AdminUserService(UserProfileRepository profiles, NotificationService notifications) {
		this.profiles = profiles;
		this.notifications = notifications;
	}

	public record AdminView(String id, String username, String name) {
	}

	@Transactional(readOnly = true)
	public List<AdminView> admins() {
		return profiles.findAdmins().stream()
			.map(this::view)
			.toList();
	}

	@Transactional
	public AdminView promote(String username, CurrentUser byAdmin) {
		UserProfile target = profiles.findByUsername(normalize(username))
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuário não encontrado."));
		if (target.admin()) {
			throw new ApiException(409, "ALREADY_ADMIN", "Este usuário já é admin.");
		}
		target.hydrateAdmin(true);
		UserProfile saved = profiles.save(target);
		notifications.create(saved.id(), "MODERACAO", byAdmin.id().value(), "Você agora é admin",
			"Um administrador promoveu sua conta. O painel completo está liberado.", "/moderador",
			"Abrir painel");
		audit("promovido", saved, byAdmin);
		return view(saved);
	}

	@Transactional
	public AdminView revoke(String username, CurrentUser byAdmin) {
		UserProfile target = profiles.findByUsername(normalize(username))
			.orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "Usuário não encontrado."));
		if (target.id().equals(byAdmin.id().value())) {
			throw new ApiException(400, "CANNOT_REVOKE_SELF",
				"Não dá para revogar a própria conta pela API — operação manual no banco.");
		}
		if (!target.admin()) {
			throw new ApiException(409, "NOT_ADMIN", "Este usuário não é admin.");
		}
		target.hydrateAdmin(false);
		UserProfile saved = profiles.save(target);
		notifications.create(saved.id(), "MODERACAO", byAdmin.id().value(), "Papel de admin revogado",
			"Um administrador revogou o acesso administrativo da sua conta.", "/feed", "Voltar ao feed");
		audit("revogado", saved, byAdmin);
		return view(saved);
	}

	private AdminView view(UserProfile profile) {
		return new AdminView(profile.id(), profile.username(), profile.name());
	}

	private void audit(String acao, UserProfile target, CurrentUser byAdmin) {
		org.slf4j.LoggerFactory.getLogger(AdminUserService.class)
			.info("admin_role_change acao={} alvo={} por={}", acao, target.id(), byAdmin.id().value());
	}

	// Mesmo padrão dos outros serviços (appoint/sanction): trim, sem mudança de caixa —
	// usernames são pesquisados como foram criados.
	private static String normalize(String username) {
		return username == null ? "" : username.trim();
	}
}
