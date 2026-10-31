package com.socialcoisa.rede.projects.adapters.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.projects.application.ProjectsService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Cards de projeto do perfil.
 *
 * Leitura PÚBLICA por username (a tela de perfil busca em paralelo, como os
 * comentários) e CRUD do dono em /me/projects. O HTML do card passa pelo
 * sanitizador na gravação; a leitura entrega ele pronto para o quadro isolado.
 */
@RestController
class UserProjectsController {

	private final ProjectsService service;
	private final CurrentUserResolver currentUserResolver;

	UserProjectsController(ProjectsService service, CurrentUserResolver currentUserResolver) {
		this.service = service;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping(value = "/users/{username}/projects", produces = "application/json")
	List<ProjectView> byUsername(@PathVariable String username) {
		return service.byUsername(username).stream().map(ProjectView::from).toList();
	}

	@GetMapping("/me/projects")
	List<ProjectView> mine(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return service.of(user.id().value()).stream().map(ProjectView::from).toList();
	}

	@PostMapping("/me/projects")
	ResponseEntity<ProjectView> create(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody ProjectRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return ResponseEntity.status(201).body(ProjectView.from(service.create(user.id().value(), request.toDomain())));
	}

	@PutMapping("/me/projects/{projectId}")
	ProjectView update(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String projectId, @RequestBody ProjectRequest request) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		return ProjectView.from(service.update(user.id().value(), projectId, request.toDomain()));
	}

	@DeleteMapping("/me/projects/{projectId}")
	ResponseEntity<Void> delete(@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@PathVariable String projectId) {
		CurrentUser user = currentUserResolver.resolve(sessionToken);
		service.delete(user.id().value(), projectId);
		return ResponseEntity.noContent().build();
	}
}
