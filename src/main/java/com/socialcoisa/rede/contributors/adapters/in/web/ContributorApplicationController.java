package com.socialcoisa.rede.contributors.adapters.in.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.contributors.application.ContributorApplicationCommand;
import com.socialcoisa.rede.contributors.application.ContributorApplicationService;
import com.socialcoisa.rede.contributors.application.port.out.VerifiedContributorReader.VerifiedContributor;
import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationSource;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.contributors.domain.ContributorExperience;
import com.socialcoisa.rede.shared.security.ClientIpResolver;
import com.socialcoisa.rede.shared.security.IpRateLimiter;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Formulário público de contribuição da landing + confirmação de quem já tem contribuição
 * verificada + fila interna.
 *
 * <p>O POST público passa pelo limite por IP e pelo gate de origem. A confirmação verificada exige
 * sessão: o handle do GitHub vem do vínculo, não do corpo, e a candidatura já nasce aprovada — o
 * GitHub é a aprovação. A listagem e a triagem manual são apenas para administradores.</p>
 */
@RestController
class ContributorApplicationController {

	private final ContributorApplicationService service;
	private final CurrentUserResolver currentUserResolver;
	private final AdminGuard adminGuard;
	private final IpRateLimiter rateLimiter;
	private final ClientIpResolver clientIpResolver;

	ContributorApplicationController(ContributorApplicationService service,
			CurrentUserResolver currentUserResolver, AdminGuard adminGuard, IpRateLimiter rateLimiter,
			ClientIpResolver clientIpResolver) {
		this.service = service;
		this.currentUserResolver = currentUserResolver;
		this.adminGuard = adminGuard;
		this.rateLimiter = rateLimiter;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/contributors/applications")
	ResponseEntity<ApplicationView> apply(@RequestBody ApplyRequest request, HttpServletRequest http) {
		if (!rateLimiter.tryAcquire(IpRateLimiter.CONTRIBUTION, clientIpResolver.resolve(http))) {
			throw new ApiException(429, "RATE_LIMITED", "Muitas tentativas. Tente de novo mais tarde.");
		}
		ContributorApplication created = service.apply(command(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationView.from(created));
	}

	/** Confirmação de dados de quem já tem contribuição verificada; aprovada pelo GitHub. */
	@PostMapping("/contributors/applications/verified")
	ResponseEntity<ApplicationView> applyVerified(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody ApplyRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		ContributorApplication saved = service.applyVerified(user.id().value(), command(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationView.from(saved));
	}

	/** Estado do visitante: verificado? já confirmou os dados? (usado pelo convite no front) */
	@GetMapping("/contributors/me")
	UserStatusView me(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return UserStatusView.from(service.statusForUser(user.id().value()));
	}

	@GetMapping("/admin/contributors/applications")
	List<AdminView> list(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(required = false) ContributorApplicationStatus status,
			@RequestParam(defaultValue = "100") int limit) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return service.list(status, limit).stream().map(AdminView::from).toList();
	}

	/** Painel de contribuintes: candidaturas + verificados pelo GitHub que ainda não confirmaram. */
	@GetMapping("/admin/contributors/panel")
	List<AdminView> panel(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return service.panel().stream()
			.map(entry -> entry.application() != null
				? AdminView.from(entry.application(), entry.verified(), entry.formPending())
				: AdminView.synthetic(entry.verified()))
			.toList();
	}

	@PatchMapping("/admin/contributors/applications/{id}")
	AdminView updateStatus(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String id, @RequestBody UpdateStatusRequest request) {
		adminGuard.requireAdmin(currentUserResolver.resolve(sessionToken));
		return AdminView.from(service.updateStatus(id, request.status()));
	}

	private static ContributorApplicationCommand command(ApplyRequest request) {
		return new ContributorApplicationCommand(request.name(), request.github(), request.email(), request.area(),
			request.experience(), request.message(), request.acceptedTerms());
	}

	record ApplyRequest(String name, String github, String email, String area, String experience, String message,
			boolean acceptedTerms) {
	}

	record UpdateStatusRequest(ContributorApplicationStatus status) {
	}

	/** Resposta pública: não devolve e-mail nem mensagem; só o protocolo e o estado. */
	record ApplicationView(String id, ContributorApplicationStatus status, Instant createdAt) {
		static ApplicationView from(ContributorApplication application) {
			return new ApplicationView(application.id(), application.status(), application.createdAt());
		}
	}

	/** Estado da conta para o convite de confirmação. */
	record UserStatusView(boolean verified, String github, boolean applicationPending, String name,
			String email, ApplicationSummary application) {
		static UserStatusView from(ContributorApplicationService.UserStatus status) {
			return new UserStatusView(status.verified(), status.github(), status.applicationPending(),
				status.name(), status.email(),
				status.application() == null ? null
					: new ApplicationSummary(status.application().id(), status.application().status(),
						status.application().source(), status.application().createdAt()));
		}
	}

	record ApplicationSummary(String id, ContributorApplicationStatus status,
			ContributorApplicationSource source, Instant createdAt) {
	}

	/**
	 * Visão interna. Candidaturas têm id; verificados que ainda não confirmaram aparecem com id
	 * nulo, `formPending=true` e `source=GITHUB_VERIFIED`.
	 */
	record AdminView(String id, String userId, String name, String github, String email, String area,
			ContributorExperience experience, String message, ContributorApplicationStatus status,
			ContributorApplicationSource source, boolean formPending, String sealName, Integer evidenceCount,
			Instant acceptedTermsAt, Instant createdAt) {

		static AdminView from(ContributorApplication application) {
			return from(application, null, false);
		}

		static AdminView from(ContributorApplication application, VerifiedContributor verified,
				boolean formPending) {
			return new AdminView(application.id(), application.userId(), application.name(), application.github(),
				application.email(), application.area(), application.experience(), application.message(),
				application.status(), application.source(), formPending,
				verified == null ? null : verified.sealName(),
				verified == null ? null : verified.evidenceCount(), application.acceptedTermsAt(),
				application.createdAt());
		}

		static AdminView synthetic(VerifiedContributor verified) {
			return new AdminView(null, verified.userId(), verified.displayName(), verified.githubLogin(), null,
				null, null, null, ContributorApplicationStatus.APPROVED,
				ContributorApplicationSource.GITHUB_VERIFIED, true, verified.sealName(),
				verified.evidenceCount(), null, verified.grantedAt());
		}
	}
}
