package com.socialcoisa.rede.blog.application;

import java.util.List;

import com.socialcoisa.rede.blog.domain.Blog;
import com.socialcoisa.rede.profile.application.NameStyleView;
import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * O blog com o que a pagina publica precisa: os dados do autor (para o cabecalho), o tema em
 * HTML/CSS (quando existe) e os posts. `subscribed` e do VISITANTE (false sem sessao), `owner`
 * diz se quem esta lendo e o dono.
 */
public record BlogView(String id, String slug, String title, String description, String themeHtml, String authorName,
		String authorUsername, String authorAvatar, String authorBadge, List<String> authorBadges,
		NameStyleView authorStyle, int posts, boolean subscribed, boolean owner, List<BlogPostView> items) {

	public static BlogView from(Blog blog, UserProfile author, int posts, boolean subscribed, boolean owner,
			List<BlogPostView> items) {
		List<String> badges = author == null ? List.of() : author.allBadges();
		return new BlogView(blog.id(), blog.slug(), blog.title(), blog.description(), blog.themeHtml(),
			author == null ? blog.slug() : author.name(),
			author == null ? blog.slug() : author.username(),
			author == null ? null : author.avatar(),
			badges.isEmpty() ? null : badges.get(0),
			badges,
			NameStyleView.from(author), posts, subscribed, owner, items);
	}

	/**
	 * O mesmo blog sem os posts. Resposta de ESCRITA nao leva a lista: ela pode ter dezenas de posts
	 * com HTML grande e estoura o registro de idempotencia que guarda a resposta (o mesmo cuidado do
	 * post de pagina). O front recarrega a pagina e busca a lista de qualquer jeito.
	 */
	public BlogView withoutItems() {
		return new BlogView(id, slug, title, description, themeHtml, authorName, authorUsername, authorAvatar,
			authorBadge, authorBadges, authorStyle, posts, subscribed, owner, List.of());
	}
}
