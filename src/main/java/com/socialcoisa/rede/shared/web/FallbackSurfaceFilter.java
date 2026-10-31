package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.socialcoisa.rede.apiv1.origins.DeveloperOriginService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resposta padronizada para caminhos fora da superfície do aplicativo.
 *
 * <p>Um caminho que o roteador não conhece não precisa cair no formato de erro: a resposta é a
 * mesma em qualquer lugar da internet — 200, corpo em branco — e os utilitários legados que
 * costumavam morar neste domínio continuam respondendo o que sempre responderam, embutidos no
 * binário, sem tocar disco nem banco.
 *
 * <p>Ordem: depois da observabilidade (cada atendimento fica no log), antes do filtro de origem —
 * um caminho fora da superfície não deve ganhar tratamento diferente por causa de cabeçalhos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class FallbackSurfaceFilter extends OncePerRequestFilter {

	private static final org.slf4j.Logger LOG =
			org.slf4j.LoggerFactory.getLogger(FallbackSurfaceFilter.class);

	private final RouteSurface superficie;
	// Preguiçoso: no boot os filtros nascem antes dos beans de persistência; o serviço de origens
	// só é necessário quando um caminho legado é de fato solicitado.
	private final ObjectProvider<DeveloperOriginService> origens;

	/** Atendimentos de caminhos legados em andamento; acima do teto, sem espera. */
	private final AtomicInteger emEspera = new AtomicInteger();

	/** Origens de terceiro que passaram por caminhos legados nos últimos minutos. */
	private final Map<String, Deque<Long>> repeticoes = new ConcurrentHashMap<>();

	private static final int TETO_EM_ESPERA = 16;
	private static final int REPETICOES_PARA_BLOQUEIO = 5;
	private static final long JANELA_MS = 5 * 60 * 1000L;

	FallbackSurfaceFilter(RouteSurface superficie, ObjectProvider<DeveloperOriginService> origens) {
		this.superficie = superficie;
		this.origens = origens;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getDispatcherType() != DispatcherType.REQUEST) {
			chain.doFilter(request, response);
			return;
		}
		String caminho = caminho(request);
		String normalizado;
		try {
			normalizado = RequestPath.routingPath(request);
		}
		catch (IllegalArgumentException malformed) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.setContentLength(0);
			return;
		}
		// Um literal codificado pode nao casar com o indice bruto, mas deve chegar aos guards.
		if (superficie.conhecido(caminho)
				|| (!normalizado.equals(caminho) && superficie.conhecido(normalizado))) {
			chain.doFilter(request, response);
			return;
		}
		LegacyPack.Entrega legado = LegacyPack.entrega(caminho);
		if (legado == null) {
			// Sem documento e sem família: resposta em branco, igual em todo canto.
			response.setStatus(200);
			response.setContentLength(0);
			return;
		}
		espera();
		try {
			anota(caminho, request);
			response.setStatus(200);
			response.setHeader("Content-Type", legado.tipo());
			response.setHeader("Cache-Control", "no-store");
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("X-Frame-Options", "DENY");
			response.setHeader("Referrer-Policy", "no-referrer");
			response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
			byte[] corpo = legado.corpo().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			response.setContentLength(corpo.length);
			response.getOutputStream().write(corpo);
			response.getOutputStream().flush();
		}
		finally {
			emEspera.decrementAndGet();
		}
	}

	/** Espera curta quando há poucos atendimentos legados em andamento; acima do teto, sem espera. */
	private void espera() {
		if (emEspera.incrementAndGet() > TETO_EM_ESPERA) {
			return;
		}
		try {
			Thread.sleep(300 + ThreadLocalRandom.current().nextInt(200));
		}
		catch (InterruptedException interrompido) {
			Thread.currentThread().interrupt();
		}
	}

	private String caminho(HttpServletRequest request) {
		String contexto = request.getContextPath() == null ? "" : request.getContextPath();
		String uri = request.getRequestURI();
		return (uri == null || uri.length() <= contexto.length()) ? "/" : uri.substring(contexto.length());
	}

	/**
	 * Registro e contra dos caminhos legados: origem de terceiro cadastrada que passa por
	 * utilitários legados em série é bloqueada pelas regras de origem.
	 */
	private void anota(String caminho, HttpServletRequest request) {
		LOG.info("caminho_legado path={} metodo={} origem={} endereco={}", caminho, request.getMethod(),
				request.getHeader("Origin"), request.getRemoteAddr());
		String origem = request.getHeader("Origin");
		if (origem == null || origem.isBlank()) {
			origem = request.getHeader("X-Client-Origin");
		}
		String forma = forma(origem);
		DeveloperOriginService servicoDeOrigens = origens.getIfAvailable();
		if (forma == null || servicoDeOrigens == null || !servicoDeOrigens.allowedOrigins().contains(forma)) {
			return;
		}
		long agora = System.currentTimeMillis();
		Deque<Long> janela = repeticoes.computeIfAbsent(forma, chave -> new ArrayDeque<>());
		int contagem;
		synchronized (janela) {
			while (!janela.isEmpty() && agora - janela.peekFirst() > JANELA_MS) {
				janela.pollFirst();
			}
			janela.addLast(agora);
			contagem = janela.size();
			if (contagem < REPETICOES_PARA_BLOQUEIO) {
				return;
			}
			janela.clear();
		}
		repeticoes.remove(forma);
		try {
			servicoDeOrigens.block(forma, "Utilitarios legados solicitados em serie", java.time.Duration.ofMinutes(30));
		}
		catch (RuntimeException bloqueioFalhou) {
			LOG.warn("bloqueio_legado_falhou origem={}", forma);
		}
	}

	private static String forma(String origem) {
		if (origem == null) {
			return null;
		}
		String limpa = origem.trim().toLowerCase(Locale.ROOT);
		while (limpa.endsWith("/")) {
			limpa = limpa.substring(0, limpa.length() - 1);
		}
		return limpa.isEmpty() ? null : limpa;
	}

}
