package com.socialcoisa.rede.importresume.adapters.in.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService;
import com.socialcoisa.rede.importresume.ResumeImportService;
import com.socialcoisa.rede.importresume.ResumePrefillService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Importacao via CURRICULO em arquivo (PDF ou texto). Terceira origem do retrato, ao lado do
 * ZIP do LinkedIn e do texto colado: a pessoa sobe o curriculo e recebe o retrato para
 * revisar no front — nada e salvo automaticamente, a inscricao continua no POST existente.
 *
 * Nada aqui mexe nos fluxos do LinkedIn: o retrato sai no mesmo formato
 * (`LinkedinImportPreview`), entao o front reaproveita as telas de revisao.
 */
@RestController
@RequestMapping("/me/import/resume")
class ResumeImportController {

	private final ResumeImportService service;
	private final ResumePrefillService prefillService;
	private final CurrentUserResolver currentUserResolver;

	ResumeImportController(ResumeImportService service, ResumePrefillService prefillService,
			CurrentUserResolver currentUserResolver) {
		this.service = service;
		this.prefillService = prefillService;
		this.currentUserResolver = currentUserResolver;
	}

	/** Sobe o curriculo (PDF ou texto) e recebe o retrato para revisar antes de salvar. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	LinkedinImportService.LinkedinImportPreview importResume(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam("file") MultipartFile file) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.parse(file);
	}

	/**
	 * Preenche a inscricao da vaga com os dados do curriculo: sobe o arquivo + o id da vaga e
	 * recebe mensagem e respostas sugeridas. So sugere — a inscricao continua sendo o
	 * `POST /jobs/{id}/applications`, com a pessoa revisando antes de enviar.
	 */
	@PostMapping(path = "/prefill/{jobId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResumeImportService.ApplyPrefillView prefillApplication(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String jobId,
			@RequestParam("file") MultipartFile file) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		String resumeText = service.extractText(file);
		return prefillService.prefill(user.id().value(), jobId, resumeText);
	}
}
