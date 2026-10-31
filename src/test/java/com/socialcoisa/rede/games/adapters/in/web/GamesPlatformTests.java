package com.socialcoisa.rede.games.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * A plataforma Games: catálogo com descoberta, página do criador, biblioteca, devlog e as garantias
 * que o pedido exige.
 *
 * O que este teste tranca, além do caminho feliz:
 *
 * - jogo não publicado não aparece no catálogo nem abre para estranhos (e o dono vê o dele);
 * - busca/filtros/ordem/paginação respondem o que prometem;
 * - **a bateria de endereços**: http, javascript:, credencial na URL, loopback, faixa privada,
 *   link-local (metadados da nuvem) e IPv6 local são recusados — é a proteção de quem JOGA, porque
 *   quem carrega a URL é o navegador da pessoa, não o servidor;
 * - dono e moderação: só o dono edita/publica novidade, só admin suspende.
 *
 * Os endereços dos jogos usam IP público literal (`https://8.8.8.8/...`) de propósito: a validação
 * resolve o host, e um teste que depende de DNS fica vermelho quando a rede do CI cai.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamesPlatformTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gplat_admin@test.com");
	}

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void catalogSearchesFiltersOrdersAndPaginates() throws Exception {
		var creator = auth.authenticate("gplat_creator1");
		var admin = auth.authenticate("gplat_admin");

		// A tag `catalogo1` é só deste teste: o banco é o mesmo para todos os métodos da classe, e
		// sem um marcador próprio a paginação e a ordem ficariam à mercê do que os outros criaram.
		publish(creator, admin, "Neon Drift", "https://8.8.8.8/neon", "Corrida neon de arcade",
			"ARCADE,RETRO,catalogo1", "RELEASED", "FREE");
		publish(creator, admin, "Padaria do Zé", "https://8.8.8.8/padaria", "Simulador de padaria",
			"SIMULACAO,catalogo1", "EARLY_ACCESS", "FREE");
		publish(creator, admin, "Cave Story BR", "https://8.8.8.8/cave", "Plataforma de caverna",
			"PLATAFORMA,catalogo1", "DEMO", "PAID");

		// Busca textual acha por título, descrição e tag. Every lookup is scoped to `catalogo1`: other
		// test classes share this database and publish games too (e.g. another ARCADE game).
		assertThat(catalogIds("?tag=catalogo1&query=neon")).containsExactly("neon-drift");
		assertThat(catalogIds("?tag=catalogo1&query=padaria")).containsExactly("padaria-do-ze");
		assertThat(catalogIds("?tag=catalogo1&query=arcade")).containsExactly("neon-drift");

		// Filtros.
		assertThat(catalogIds("?tag=catalogo1&genre=PLATAFORMA")).containsExactly("cave-story-br");
		assertThat(catalogIds("?tag=catalogo1&stage=EARLY_ACCESS")).containsExactly("padaria-do-ze");
		assertThat(catalogIds("?tag=catalogo1&distribution=PAID")).containsExactly("cave-story-br");
		assertThat(catalogIds("?tag=retro")).contains("neon-drift").doesNotContain("padaria-do-ze", "cave-story-br");

		// Ordem: por título é alfabética e estável (só os jogos deste teste).
		assertThat(catalogIds("?tag=catalogo1&order=title"))
			.containsExactly("cave-story-br", "neon-drift", "padaria-do-ze");

		// Paginação: duas por página, com cursor até acabar.
		MvcResult first = mockMvc.perform(get("/games").param("tag", "catalogo1").param("limit", "2")
				.param("order", "title"))
			.andExpect(status().isOk())
			.andReturn();
		var page = objectMapper.readTree(first.getResponse().getContentAsString());
		assertThat(page.get("items")).hasSize(2);
		String cursor = page.get("nextCursor").asText();
		assertThat(cursor).isNotBlank();

		MvcResult second = mockMvc.perform(get("/games").param("tag", "catalogo1").param("limit", "2")
				.param("order", "title").param("cursor", cursor))
			.andExpect(status().isOk())
			.andReturn();
		var secondPage = objectMapper.readTree(second.getResponse().getContentAsString());
		assertThat(secondPage.get("items")).hasSize(1);
		assertThat(secondPage.get("nextCursor").isNull()).isTrue();
	}

	@Test
	void urlGuardRejectsUnsafeTargets() throws Exception {
		var creator = auth.authenticate("gplat_creator2");

		for (String bad : List.of(
				"http://8.8.8.8/jogo",                 // sem https
				"javascript:alert(1)",                 // esquema perigoso
				"data:text/html,<h1>oi</h1>",          // esquema perigoso
				"https://127.0.0.1/jogo",              // loopback
				"https://localhost/jogo",              // loopback por nome
				"https://10.1.2.3/jogo",               // faixa privada IPv4
				"https://192.168.0.10/jogo",           // faixa privada IPv4
				"https://169.254.169.254/latest",      // metadados da nuvem (link-local)
				"https://[::1]/jogo",                  // loopback IPv6
				"https://usuario:senha@8.8.8.8/jogo",  // credencial na URL
				"https:///sem-host",                   // sem host
				"https://8.8.8.8/jogo com espaço")) {  // espaço
			mockMvc.perform(post("/games")
					.header("X-Session-Token", creator.token())
					.header("Idempotency-Key", "gplat-bad-" + Math.abs(bad.hashCode()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("title", "Jogo Ruim", "url", bad))))
				.andExpect(status().isBadRequest());
		}

		// E o endereço público legítimo passa.
		mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gplat-good-url")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Jogo Bom", "url", "https://8.8.8.8/bom"))))
			.andExpect(status().isOk());
	}

	@Test
	void unpublishedGameStaysOutOfCatalogAndOwnerActionsAreGuarded() throws Exception {
		var creator = auth.authenticate("gplat_creator3");
		var other = auth.authenticate("gplat_other3");

		String gameId = submit(creator, "Rascunho Secreto", "https://8.8.8.8/secreto");

		// Não publicado: fora do catálogo e 404 para quem não é dono.
		assertThat(catalogIds("?query=secreto")).isEmpty();
		mockMvc.perform(get("/games/gplat-creator3/rascunho-secreto")).andExpect(status().isNotFound());
		mockMvc.perform(get("/games/gplat-creator3/rascunho-secreto")
				.header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"));

		// Quem não é dono não edita nem publica novidade.
		mockMvc.perform(patch("/games/" + gameId)
				.header("X-Session-Token", other.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Invadido", "url", "https://8.8.8.8/x"))))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/games/" + gameId + "/updates")
				.header("X-Session-Token", other.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Novidade alheia"))))
			.andExpect(status().isForbidden());

		// Favoritar jogo não publicado é recusado (não há o que guardar).
		mockMvc.perform(post("/games/" + gameId + "/favorite").header("X-Session-Token", other.token()))
			.andExpect(status().isConflict());
	}

	@Test
	void developerPageFollowFavoritesAndDevlog() throws Exception {
		var creator = auth.authenticate("gplat_creator4");
		var player = auth.authenticate("gplat_player4");
		var admin = auth.authenticate("gplat_admin");

		String gameId = publish(creator, admin, "Café com Código", "https://8.8.8.8/cafe", "Jogo sobre café",
			"SIMULACAO,devpage1", "RELEASED", "FREE");

		// Página do criador: identidade do Énóis + o que ele preencheu.
		mockMvc.perform(put("/games/profile")
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"displayName", "Estúdio Café", "headline", "jogos pequenos e honestos",
					"bio", "Fazemos jogos de navegador.", "links", "https://exemplo.com,https://github.com/cafe"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Estúdio Café"))
			.andExpect(jsonPath("$.games[0].slug").value("cafe-com-codigo"));

		// Acompanhar é de mão única e idempotente.
		mockMvc.perform(post("/games/gplat-creator4/follow").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.following").value(true));
		mockMvc.perform(get("/games/gplat-creator4/profile").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.following").value(true))
			.andExpect(jsonPath("$.followers").value(1));
		mockMvc.perform(delete("/games/gplat-creator4/follow").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.following").value(false));
		// Ninguém acompanha a si mesmo.
		mockMvc.perform(post("/games/gplat-creator4/follow").header("X-Session-Token", creator.token()))
			.andExpect(status().isBadRequest());

		// Biblioteca: guardar, listar e desguardar.
		mockMvc.perform(post("/games/" + gameId + "/favorite").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorited").value(true));
		mockMvc.perform(get("/games/library").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].slug").value("cafe-com-codigo"))
			.andExpect(jsonPath("$[0].favorited").value(true));
		mockMvc.perform(delete("/games/" + gameId + "/favorite").header("X-Session-Token", player.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/games/library").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		// Devlog: o dono publica, todo mundo lê, e a contagem aparece nos números do jogo.
		mockMvc.perform(post("/games/" + gameId + "/updates")
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Versão 1.2", "body", "Corrigi o bug do café frio.", "version", "1.2"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value("1.2"));
		mockMvc.perform(get("/games/" + gameId + "/updates"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("Versão 1.2"));

		// "Jogar" conta a sessão no servidor (é daqui que sai a ordenação por mais jogados).
		mockMvc.perform(post("/games/" + gameId + "/play").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plays").value(1));
		mockMvc.perform(get("/games/" + gameId + "/stats"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plays").value(1))
			.andExpect(jsonPath("$.players").value(1))
			.andExpect(jsonPath("$.updates").value(1));
		// O mais jogado lidera a ordem por plays. Scoped to this test's own tag: games from other test
		// classes (same database) may have more plays or an active paid boost.
		var primeiro = firstCatalogItem("?tag=devpage1&order=plays");
		org.assertj.core.api.Assertions.assertThat(primeiro.get("slug").asText()).isEqualTo("cafe-com-codigo");
	}

	private tools.jackson.databind.JsonNode firstCatalogItem(String query) throws Exception {
		MvcResult result = mockMvc.perform(get("/games" + query)).andExpect(status().isOk()).andReturn();
		var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		return items.get(0);
	}

	@Test
	void suspensionTakesTheGameOffTheAirWithoutDeletingIt() throws Exception {
		var creator = auth.authenticate("gplat_creator5");
		var admin = auth.authenticate("gplat_admin");
		var player = auth.authenticate("gplat_player5");

		String gameId = publish(creator, admin, "Jogo Suspeito", "https://8.8.8.8/suspeito", "Para suspender",
			"SUSPEITO", "RELEASED", "FREE");

		// Alguém guarda o jogo antes da suspensão.
		mockMvc.perform(post("/games/" + gameId + "/favorite").header("X-Session-Token", player.token()))
			.andExpect(status().isOk());

		// Quem não é admin não suspende.
		mockMvc.perform(post("/admin/games/" + gameId + "/suspend").header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "quero tirar"))))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/admin/games/" + gameId + "/suspend").header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "violação de direitos"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		// Sai do catálogo, some para os outros e sai da biblioteca de quem guardou — mas o registro
		// continua: o dono ainda vê o próprio jogo e a moderação pode devolver ao ar.
		assertThat(catalogIds("?query=suspeito")).isEmpty();
		mockMvc.perform(get("/games/gplat-creator5/jogo-suspeito")).andExpect(status().isNotFound());
		mockMvc.perform(get("/games/library").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
		mockMvc.perform(get("/games/gplat-creator5/jogo-suspeito").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mockMvc.perform(post("/admin/games/" + gameId + "/unsuspend").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(catalogIds("?query=suspeito")).containsExactly("jogo-suspeito");
	}

	// --- apoio ---

	private List<String> catalogIds(String query) throws Exception {
		MvcResult result = mockMvc.perform(get("/games" + query)).andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("items").valueStream()
			.map(node -> node.get("slug").asText()).toList();
	}

	private String submit(TestAuthHelper.AuthSession creator, String title, String url) throws Exception {
		MvcResult result = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gplat-submit-" + title.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", title, "url", url))))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	/** Envia com a ficha completa e publica pela moderação — o caminho real até aparecer no catálogo. */
	private String publish(TestAuthHelper.AuthSession creator, TestAuthHelper.AuthSession admin, String title,
			String url, String description, String genres, String stage, String distribution) throws Exception {
		MvcResult result = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gplat-publish-" + title.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", title, "url", url, "description", description,
					"tags", genres))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

		// A ficha entra na edição (o envio simples continua aceitando só o essencial).
		mockMvc.perform(patch("/games/" + gameId)
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", title, "url", url, "description", description,
					"tags", genres, "genres", genres, "stage", stage, "distribution", distribution))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/admin/games/" + gameId + "/approve").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk());
		return gameId;
	}
}
