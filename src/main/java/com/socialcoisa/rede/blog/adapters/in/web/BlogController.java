package com.socialcoisa.rede.blog.adapters.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.blog.application.BlogPostView;
import com.socialcoisa.rede.blog.application.BlogService;
import com.socialcoisa.rede.blog.application.BlogSummaryView;
import com.socialcoisa.rede.blog.application.BlogView;
import com.socialcoisa.rede.blog.domain.BlogPostFormat;
import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;
import com.socialcoisa.rede.shared.web.StatusResponse;

/**
 * Blogs: a vitrine (aba Blogs) e a pagina publica do cantinho de cada pessoa.
 *
 * Leitura e publica de proposito — `/blog/{username}` e o link que a pessoa compartilha, e exigir
 * sessao ali derrubaria quem recebeu o link no login (mesma regra do post e do perfil). A sessao,
 * quando existe, so acrescenta o estado do visitante (se ele ja segue o blog / se o blog e dele).
 */
@RestController
@RequestMapping("/blogs")
class BlogController {

	private static final int MAX_LIMIT = 100;

	private final BlogService blogService;
	private final com.socialcoisa.rede.blog.application.BlogNewsletterService newsletter;
	private final CurrentUserResolver currentUserResolver;
	private final IdempotencyApplicationService idempotency;

	BlogController(BlogService blogService, com.socialcoisa.rede.blog.application.BlogNewsletterService newsletter,
			CurrentUserResolver currentUserResolver, IdempotencyApplicationService idempotency) {
		this.blogService = blogService;
		this.newsletter = newsletter;
		this.currentUserResolver = currentUserResolver;
		this.idempotency = idempotency;
	}

	@GetMapping
	List<BlogSummaryView> directory(@RequestParam(defaultValue = "50") int limit,
			@RequestParam(required = false) String query) {
		return blogService.directory(query, Math.clamp(limit, 1, MAX_LIMIT));
	}

	@GetMapping("/{slug}")
	BlogView blog(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String slug) {
		CurrentUser user = resolveSafely(userId, sessionToken);
		return blogService.blogOf(slug, user == null ? null : user.id().value())
			.orElseThrow(() -> new ApiException(404, "BLOG_NOT_FOUND", "Blog nao encontrado."));
	}

	/** O meu blog, para o editor. `null` = ainda nao existe (a tela oferece criar). */
	@GetMapping("/me")
	BlogView mine(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return blogService.mine(user.id().value()).orElse(null);
	}

	@PutMapping("/me")
	ResponseEntity<String> save(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody BlogRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(request);
		return idempotency.execute(user.id(), "PUT", "/api/blogs/me", idempotencyKey, hash,
			() -> new CommandResponse(200, blogService
				.saveMine(user.id().value(), request.title(), request.description(), request.themeHtml())
				.withoutItems()));
	}

	@PostMapping("/me/posts")
	ResponseEntity<String> publish(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody BlogPostRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(request);
		return idempotency.execute(user.id(), "POST", "/api/blogs/me/posts", idempotencyKey, hash,
			() -> new CommandResponse(201, blogService.publish(user.id().value(), request.title(), request.body(),
				request.format(), request.notifySubscribers() == null || request.notifySubscribers())));
	}

	@PatchMapping("/me/posts/{postId}")
	ResponseEntity<String> edit(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@PathVariable String postId, @RequestBody BlogPostRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		String hash = idempotency.hash(request);
		return idempotency.execute(user.id(), "PATCH", "/api/blogs/me/posts/" + postId, idempotencyKey, hash,
			() -> new CommandResponse(200, blogService.edit(user.id().value(), postId, request.title(),
				request.body(), request.format())));
	}

	@DeleteMapping("/me/posts/{postId}")
	StatusResponse delete(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String postId) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		blogService.delete(user.id().value(), postId);
		return new StatusResponse(postId, "DELETED");
	}

	@PostMapping("/{slug}/subscription")
	StatusResponse subscribe(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String slug) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		blogService.subscribe(user.id().value(), slug);
		return new StatusResponse(slug, "SUBSCRIBED");
	}

	@DeleteMapping("/{slug}/subscription")
	StatusResponse unsubscribe(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String slug) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		blogService.unsubscribe(user.id().value(), slug);
		return new StatusResponse(slug, "NOT_SUBSCRIBED");
	}

	/** Sessao opcional: sem sessao (ou com sessao invalida) a leitura publica continua. */
	private CurrentUser resolveSafely(String userId, String sessionToken) {
		try {
			return currentUserResolver.resolve(userId, sessionToken);
		}
		catch (Exception ignored) {
			return null;
		}
	}

	record BlogRequest(String title, String description, String themeHtml) {
	}

	/**
	 * Descadastro num clique do e-mail da newsletter (sem sessao): o token cifrado carrega
	 * blog+assinante. Responde uma pagina HTML simples — quem clica vem de um cliente de e-mail,
	 * nao do app.
	 */
	@GetMapping(value = "/descadastrar", produces = org.springframework.http.MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> descadastrar(@RequestParam(required = false) String token) {
		boolean ok = newsletter.unsubscribeByToken(token);
		String titulo = ok ? "Inscrição cancelada" : "Link inválido";
		String mensagem = ok
			? "Pronto: você não vai mais receber os e-mails deste blog. A inscrição no Énóis segue, e você pode voltar quando quiser."
			: "Este link de descadastro não é válido (ou já foi usado). Abra o blog no Énóis e cancele por lá.";
		String html = """
			<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8">
			<meta name="viewport" content="width=device-width, initial-scale=1.0"><title>%s</title></head>
			<body style="margin:0;background:#f8f7ff;font-family:'Inter',system-ui,sans-serif;display:grid;place-items:center;min-height:100vh">
			<div style="max-width:440px;background:#fff;border-radius:16px;padding:32px;margin:16px;box-shadow:0 8px 24px -8px rgba(99,102,241,.15);text-align:center">
			<h1 style="margin:0 0 12px;color:#1a1a2e;font-size:20px">%s</h1>
			<p style="margin:0 0 20px;color:#4b5563;font-size:14px;line-height:1.6">%s</p>
			<a href="%s" style="display:inline-block;padding:12px 24px;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;text-decoration:none;border-radius:999px;font-weight:600;font-size:14px">Ir para o Énóis</a>
			</div></body></html>
			""".formatted(titulo, titulo, mensagem, frontendUrl());
		return ResponseEntity.status(ok ? 200 : 400)
			.contentType(org.springframework.http.MediaType.TEXT_HTML)
			.body(html);
	}

	private String frontendUrl() {
		return "https://enois.ia.br";
	}

	record BlogPostRequest(String title, String body, BlogPostFormat format, Boolean notifySubscribers) {
	}
}
