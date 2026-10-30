package com.socialcoisa.rede.shared.application;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Cache em memoria do processo.
 *
 * Serve para contadores e agregados que custam uma varredura no banco e nao precisam ser exatos ao
 * segundo — hoje o contador de usuarios ativos da landing. E de proposito em memoria (nao Redis):
 * o dado e recalculavel, cada pod pode ter o seu proprio numero por alguns minutos, e assim nao
 * entra dependencia de infra nova por causa de um contador.
 *
 * Lista de caches fechada: cache sem TTL e sem dono conhecido virou fonte de bug em todo lugar que
 * eu vi; aqui cada um precisa ser declarado com o seu frescor.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

	/** Frescor do contador da comunidade: o numero pode ficar ate 10 min atras do banco. */
	private static final Duration COMMUNITY_STATS_TTL = Duration.ofMinutes(10);

	/**
	 * Cartao de compartilhamento das paginas (PNG desenhado no servidor). Uma hora: o cartao so muda
	 * quando a pagina e editada, e o cabecalho HTTP ja pede um dia de cache para as plataformas.
	 */
	private static final Duration PAGE_PREVIEW_CARD_TTL = Duration.ofHours(1);

	/** Vagas externas so mudam na importacao (boot/meia-noite): 5 min de cache evitam bater no banco. */
	private static final Duration EXTERNAL_JOBS_TTL = Duration.ofMinutes(5);

	/**
	 * Busca de GIF do seletor. Dez minutos: o termo repetido nao gasta a cota do provedor de novo, e
	 * a chave inclui o termo — por isso o teto de entradas e maior que o dos outros caches.
	 */
	private static final Duration GIF_SEARCH_TTL = Duration.ofMinutes(10);
	private static final int GIF_SEARCH_MAX_ENTRIES = 200;

	/**
	 * Repositórios e README públicos do GitHub, por login (importação para o perfil). Poucos minutos:
	 * o suficiente para abrir o diálogo e importar sem repetir a ida ao GitHub, curto o bastante para
	 * o repositório recém-criado aparecer logo. O teto de entradas é de logins distintos.
	 */
	private static final Duration GITHUB_PROFILE_TTL = Duration.ofMinutes(3);
	private static final int GITHUB_PROFILE_MAX_ENTRIES = 200;

	@Bean
	CacheManager cacheManager() {
		SimpleCacheManager manager = new SimpleCacheManager();
		manager.setCaches(List.of(cache("communityStats", COMMUNITY_STATS_TTL),
			cache("externalJobs", EXTERNAL_JOBS_TTL),
			cache("pagePreviewCards", PAGE_PREVIEW_CARD_TTL),
			cache("gifSearch", GIF_SEARCH_TTL, GIF_SEARCH_MAX_ENTRIES),
			cache("githubRepositories", GITHUB_PROFILE_TTL, GITHUB_PROFILE_MAX_ENTRIES),
			cache("githubReadme", GITHUB_PROFILE_TTL, GITHUB_PROFILE_MAX_ENTRIES)));
		return manager;
	}

	private CaffeineCache cache(String name, Duration ttl) {
		return cache(name, ttl, 16);
	}

	private CaffeineCache cache(String name, Duration ttl, int maxEntries) {
		return new CaffeineCache(name,
			Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxEntries).build());
	}
}
