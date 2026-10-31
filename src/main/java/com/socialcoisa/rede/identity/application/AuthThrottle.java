package com.socialcoisa.rede.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Throttle de credenciais (F-02/F-04 do pentest): o RateLimiter global ("auth", 20/min por
 * pod) é compartilhado por TODOS os usuários — não segura brute force dirigido a UMA conta
 * nem cadastro em massa de UM IP. Aqui: janelas por CHAVE (conta no login, IP no signup)
 * guardadas em memória com expiração — a aplicação continua stateless.
 *
 * <p>Login: after 5 failures in 1 minute for the same account AND IP, each attempt is delayed
 * progressively (no lock; o acerto zera o contador). Once failures against the account from any
 * network pass 4x that limit, a network that itself failed on the account in the last minute gets
 * 429 LOGIN_THROTTLED right away (no thread held asleep). Spraying one account from many IPs is
 * stopped, while the owner on a network with no recent failure on it is never refused.
 * Signup: 10 por hora por IP. Os tetos são configuráveis — o perfil dev/testes abre
 * (a suíte cria centenas de contas do mesmo IP).</p>
 */
@Component
public class AuthThrottle {

	private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
	private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);
	/** Teto do mapa: evita crescer sem fim sob ataque distribuído (as chaves velhas expiram). */
	private static final int MAX_KEYS = 50_000;
	/** Per-account (any network) failures needed before the account-wide throttle starts, as a multiple. */
	private static final int ACCOUNT_FAILURE_FACTOR = 4;
	private static final long ACCOUNT_IP_MAX_DELAY_MILLIS = 2_000L;

	private final int loginMaxFalhas;
	private final int signupMaxPorJanela;
	private final int maxKeys;

	private final Map<String, Janela> falhasLogin = new ConcurrentHashMap<>();
	private final Map<String, Janela> falhasPorConta = new ConcurrentHashMap<>();
	private final Map<String, Janela> signupsPorIp = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Autowired
	public AuthThrottle(
			@Value("${rede.auth.throttle.login-max-failures:5}") int loginMaxFalhas,
			@Value("${rede.auth.throttle.signup-per-hour:10}") int signupMaxPorJanela) {
		this(loginMaxFalhas, signupMaxPorJanela, MAX_KEYS);
	}

	AuthThrottle(int loginMaxFalhas, int signupMaxPorJanela, int maxKeys) {
		this.loginMaxFalhas = loginMaxFalhas;
		this.signupMaxPorJanela = signupMaxPorJanela;
		this.maxKeys = maxKeys;
	}

	/**
	 * Recusa progressiva, nunca bloqueio permanente: o lockout por conta deixava qualquer pessoa
	 * derrubar a vítima com 5 senhas erradas, inclusive impedindo a senha CORRETA. Keyed by
	 * account + IP, so failures from another network never slow the owner down.
	 *
	 * Não usa Thread.sleep: segurar a thread do Tomcat permitia DoS por esgotamento de threads
	 * (atacante com IPs distribuídos ocupa todas as threads dormindo). Em vez disso, retorna 429
	 * com Retry-After e libera a thread imediatamente — o cliente que espere.
	 */
	public void exigeLoginPermitido(String email, String ip) {
		if (accountThrottled(email, ip)) {
			throw new ApiException(429, "LOGIN_THROTTLED",
				"Muitas tentativas de entrar nesta conta. Aguarde um minuto e tente de novo.");
		}
		long millis = loginDelayMillis(email, ip);
		if (millis <= 0) {
			return;
		}
		long seconds = Math.max(1, (millis + 999) / 1000);
		throw retryAfter(seconds);
	}

	private static ApiException retryAfter(long seconds) {
		return new ApiException(429, "LOGIN_THROTTLED",
			"Aguarde " + seconds + " segundo" + (seconds > 1 ? "s" : "") + " e tente de novo.")
			.withRetryAfter(seconds);
	}

	/** Progressive delay for this account from this network; 0 below the failure limit. */
	long loginDelayMillis(String email, String ip) {
		int porRede = falhasRecentes(falhasLogin, loginKey(email, ip));
		return porRede >= loginMaxFalhas ? Math.min(ACCOUNT_IP_MAX_DELAY_MILLIS, 200L * porRede) : 0;
	}

	/**
	 * The account is under a spray AND this network took part in it. A network with no recent
	 * failure on the account (the owner at home) is never refused, so nobody can lock the owner out.
	 */
	boolean accountThrottled(String email, String ip) {
		return falhasRecentes(falhasPorConta, normaliza(email)) >= loginMaxFalhas * ACCOUNT_FAILURE_FACTOR
			&& falhasRecentes(falhasLogin, loginKey(email, ip)) > 0;
	}

	private int falhasRecentes(Map<String, Janela> mapa, String chave) {
		Janela janela = mapa.get(chave);
		if (janela == null) {
			return 0;
		}
		janela.podado(Instant.now(), LOGIN_WINDOW);
		return janela.contagem();
	}

	/** Falha de credenciais: conta a janela (o êxito limpa). */
	public void registraFalhaLogin(String email, String ip) {
		registra(falhasLogin, loginKey(email, ip), LOGIN_WINDOW);
		registra(falhasPorConta, normaliza(email), LOGIN_WINDOW);
	}

	private void registra(Map<String, Janela> mapa, String chave, Duration janela) {
		abreEspaco(mapa, janela);
		mapa.compute(chave, (k, existente) -> {
			Instant agora = Instant.now();
			if (existente == null || Duration.between(existente.ultimo(), agora).compareTo(janela) > 0) {
				return new Janela(agora);
			}
			existente.registra(agora);
			return existente;
		});
	}

	/**
	 * Keeps the map under its ceiling without resetting live counters wholesale: expired windows go
	 * first; if that is not enough, the tenth with the fewest hits (oldest first) goes. A flood of
	 * one-off keys evicts itself, never the account that is actually being attacked.
	 */
	private void abreEspaco(Map<String, Janela> mapa, Duration janela) {
		if (mapa.size() < maxKeys) {
			return;
		}
		Instant agora = Instant.now();
		mapa.values().removeIf(existente -> Duration.between(existente.ultimo(), agora).compareTo(janela) > 0);
		if (mapa.size() < maxKeys) {
			return;
		}
		mapa.entrySet().stream()
			.sorted(java.util.Comparator.comparingInt((Map.Entry<String, Janela> e) -> e.getValue().contagem())
				.thenComparing(e -> e.getValue().ultimo()))
			.limit(Math.max(1, maxKeys / 10))
			.map(Map.Entry::getKey)
			.toList()
			.forEach(mapa::remove);
	}

	/** Login com sucesso: os contadores da conta zeram. */
	public void limpaLogin(String email, String ip) {
		falhasLogin.remove(loginKey(email, ip));
		falhasPorConta.remove(normaliza(email));
	}

	private String loginKey(String email, String ip) {
		return normaliza(email) + "|" + normaliza(ip);
	}

	/**
	 * O IP pode criar conta? (10 por hora por IP).
	 * Quando um fingerprint de dispositivo esta disponivel, tambem limita por fingerprint —
	 * detecta o mesmo navegador criando contas atras de proxies diferentes.
	 */
	public void exigeSignupPermitido(String ip) {
		exigeSignupPermitido(ip, null);
	}

	public void exigeSignupPermitido(String ip, String deviceFingerprint) {
		if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
			verificaJanela("fp:" + normaliza(deviceFingerprint));
		}
		verificaJanela("ip:" + normaliza(ip));
	}

	private void verificaJanela(String chave) {
		Janela janela = signupsPorIp.get(chave);
		Instant agora = Instant.now();
		if (janela != null) {
			janela.podado(agora, SIGNUP_WINDOW);
			if (janela.contagem() >= signupMaxPorJanela) {
				throw new ApiException(429, "SIGNUP_THROTTLED",
					"Muitas contas criadas a partir desta rede. Tenta de novo mais tarde.");
			}
		}
		registra(signupsPorIp, chave, SIGNUP_WINDOW);
	}

	private String normaliza(String valor) {
		return valor == null ? "" : valor.trim().toLowerCase();
	}

	/** Janela deslizante de timestamps (podados a cada leitura — memória bounded). */
	private static final class Janela {

		// A queue, not a set: two failures in the same clock tick are two failures.
		private final java.util.Queue<Instant> marcas = new java.util.concurrent.ConcurrentLinkedQueue<>();
		private Instant ultimo;

		private Janela(Instant primeiro) {
			registra(primeiro);
		}

		private void registra(Instant agora) {
			ultimo = agora;
			marcas.add(agora);
		}

		private void podado(Instant agora, Duration janela) {
			marcas.removeIf(marca -> Duration.between(marca, agora).compareTo(janela) > 0);
		}

		private int contagem() {
			return marcas.size();
		}

		private Instant ultimo() {
			return ultimo;
		}
	}
}
