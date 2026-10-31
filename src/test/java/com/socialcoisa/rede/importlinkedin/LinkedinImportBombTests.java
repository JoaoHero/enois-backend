package com.socialcoisa.rede.importlinkedin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/** A tiny archive that expands far beyond the import budget is refused with a 4xx, never a 500. */
@SpringBootTest
@AutoConfigureMockMvc
class LinkedinImportBombTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void highRatioArchiveIsRejectedWith413() throws Exception {
		var session = new TestAuthHelper(mockMvc, objectMapper).authenticate("linkedin_bomb");
		var bomb = LinkedinImportServiceTests.positionsBomb(60 * 1024 * 1024);

		mockMvc.perform(multipart("/me/import/linkedin").file(bomb).header("X-Session-Token", session.token()))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("IMPORT_TOO_LARGE"));
	}
}
