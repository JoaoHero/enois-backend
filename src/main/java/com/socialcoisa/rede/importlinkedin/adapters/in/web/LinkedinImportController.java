package com.socialcoisa.rede.importlinkedin.adapters.in.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService;
import com.socialcoisa.rede.importlinkedin.application.LinkedinContactInviteService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Importação do ARQUIVO DE DADOS do LinkedIn (Configurações → "Obter uma cópia dos seus
 * dados" → ZIP). Devolve o RETRATO parseado — nada é salvo automaticamente: a pessoa
 * revisa no front e salva pelas telas de perfil.
 */
@RestController
@RequestMapping("/me/import/linkedin")
class LinkedinImportController {

	private final LinkedinImportService service;
	private final LinkedinContactInviteService contacts;
	private final CurrentUserResolver currentUserResolver;

	LinkedinImportController(LinkedinImportService service, LinkedinContactInviteService contacts,
			CurrentUserResolver currentUserResolver) {
		this.service = service;
		this.contacts = contacts;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	LinkedinImportService.LinkedinImportPreview importArchive(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam("file") MultipartFile file) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.parse(file);
	}

	/**
	 * O caminho SEM ARQUIVO: a pessoa cola o texto do próprio perfil do LinkedIn (o formato
	 * que o navegador copia) e recebe o mesmo retrato da importação por ZIP.
	 */
	@PostMapping("/texto")
	LinkedinImportService.LinkedinImportPreview importText(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody TextoRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.parseTexto(request == null ? null : request.texto());
	}

	/**
	 * Importa o Connections.csv (do ZIP de dados ou um CSV avulso) e, se o convite automático
	 * estiver ligado, envia as solicitações para quem já tem conta no Énóis.
	 */
	@PostMapping(value = "/contatos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	LinkedinContactInviteService.ImportResult importContacts(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam("file") MultipartFile file) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return contacts.importContacts(user.id().value(), service.parseContacts(file));
	}

	/** Quantos contatos importados, convidados, já conectados etc. e o estado do botão. */
	@GetMapping("/contatos/resumo")
	LinkedinContactInviteService.Summary contactsSummary(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return contacts.summary(user.id().value());
	}

	/** Liga/desliga o convite automático; ligar já processa a fila existente. */
	@PutMapping("/contatos/auto-convite")
	LinkedinContactInviteService.Summary setAutoInvite(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody AutoInviteRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return contacts.setAutoInvite(user.id().value(), request != null && request.enabled());
	}

	/** Quem foi convidado, quem já era conexão e quem não casou com ninguém. */
	@GetMapping("/contatos")
	List<LinkedinContactInviteService.InviteView> contacts(
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestParam(defaultValue = "200") int limit) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return contacts.list(user.id().value(), limit);
	}

	record TextoRequest(String texto) {
	}

	record AutoInviteRequest(boolean enabled) {
	}
}
