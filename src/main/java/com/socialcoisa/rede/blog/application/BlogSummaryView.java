package com.socialcoisa.rede.blog.application;

import com.socialcoisa.rede.blog.domain.Blog;
import com.socialcoisa.rede.profile.application.NameStyleView;
import com.socialcoisa.rede.profile.domain.UserProfile;

/** Um blog na vitrine (aba Blogs): o suficiente para listar e linkar. */
public record BlogSummaryView(String slug, String title, String description, String authorName, String authorUsername,
		String authorAvatar, NameStyleView authorStyle, int posts) {

	public static BlogSummaryView from(Blog blog, UserProfile author, int posts) {
		return new BlogSummaryView(blog.slug(), blog.title(), blog.description(),
			author == null ? blog.slug() : author.name(),
			author == null ? blog.slug() : author.username(),
			author == null ? null : author.avatar(),
			NameStyleView.from(author),
			posts);
	}
}
