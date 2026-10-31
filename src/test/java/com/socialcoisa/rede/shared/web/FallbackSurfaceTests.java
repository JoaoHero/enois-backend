package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Todo caminho fora da superfície do roteador recebe a mesma resposta (200, corpo em branco) e os
 * documentos legados continuam sendo servidos; o que o roteador conhece — inclusive sob o alias
 * <code>/v1</code> — passa ao aplicativo normalmente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FallbackSurfaceTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdminRouteInterceptor adminRouteInterceptor;

	@Autowired
	private FallbackSurfaceFilter fallbackSurfaceFilter;

	@Test
	void caminhoForaDaSuperficieRespondeVazio() throws Exception {
		mockMvc.perform(get("/caminho_sem_atribuicao_9f2c"))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
	}

	@Test
	void escritaForaDaSuperficieTambemRespondeVazio() throws Exception {
		mockMvc.perform(post("/caminho_sem_atribuicao_9f2c"))
				.andExpect(status().isOk())
				.andExpect(content().string(""));
	}

	@Test
	void caminhoDaSuperficieRespondeNormal() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
	}

	@Test
	void aliasDeVersaoPassaAoAplicativo() throws Exception {
		mockMvc.perform(get("/v1/health"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
	}

	@Test
	void padraoComVariavelSegueAoAplicativo() throws Exception {
		MvcResult resultado = mockMvc.perform(get("/feed/posts/id_que_nao_existe_9f2c")).andReturn();
		boolean respostaPadronizada = resultado.getResponse().getStatus() == 200
				&& resultado.getResponse().getContentAsString().isEmpty();
		assertThat(respostaPadronizada).isFalse();
	}

	@Test
	void documentoLegadoRespondeComCorpo() throws Exception {
		String caminho = LegacyPack.exatos()[0];
		MvcResult resultado = mockMvc.perform(get(caminho)).andReturn();
		assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
		assertThat(resultado.getResponse().getContentAsString()).isNotEmpty();
	}

	@Test
	void familiaLegadaRespondeComCorpo() throws Exception {
		String caminho = "/painel_antigo_9f2c.php";
		assertThat(LegacyPack.familia(caminho)).isNotNull();
		MvcResult resultado = mockMvc.perform(get(caminho)).andReturn();
		assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
		assertThat(resultado.getResponse().getContentAsString()).isNotEmpty();
	}


	@Test
	void literalCodificadoContinuaSendoRotaConhecida() throws Exception {
		mockMvc.perform(get(java.net.URI.create("/auth/%63onfig")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.captchaMode").exists());
	}

	@Test
	void caminhoAdminCodificadoNaoPulaOInterceptorSemController() {
		assertThatThrownBy(() -> adminRouteInterceptor.preHandle(
				new MockHttpServletRequest("GET", "/%61dmin/support"),
				new MockHttpServletResponse(), new Object()))
			.isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(401));
	}

	@Test
	void barraDuplaNaoInventaRotaPublicaConhecida() throws Exception {
		mockMvc.perform(get(java.net.URI.create("/auth//config")))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
	}

	@Test
	void parametroDeConsultaNaoViraCaminhoAdministrativo() throws Exception {
		mockMvc.perform(get("/auth/config").queryParam("next", "/%61dmin/support"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.captchaMode").exists());
	}

	@Test
	void dadosPublicosEPrefixosParecidosNaoExigemSessaoAdmin() throws Exception {
		for (String path : java.util.List.of("/users/%61dmin", "/administer/support", "/admins/support")) {
			assertThat(adminRouteInterceptor.preHandle(new MockHttpServletRequest("GET", path),
					new MockHttpServletResponse(), new Object())).isTrue();
		}
	}

	@Test
	void escapeInvalidoQueAlcanceOFiltroRecebe400() throws Exception {
		for (String path : java.util.List.of("/admin/%zz", "/admin/%")) {
			var request = new MockHttpServletRequest("GET", path);
			var response = new MockHttpServletResponse();
			var continued = new java.util.concurrent.atomic.AtomicBoolean();
			fallbackSurfaceFilter.doFilter(request, response,
				(nextRequest, nextResponse) -> continued.set(true));
			assertThat(response.getStatus()).isEqualTo(400);
			assertThat(continued.get()).isFalse();
		}
	}
}
