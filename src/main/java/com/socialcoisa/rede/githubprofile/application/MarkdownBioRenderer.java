package com.socialcoisa.rede.githubprofile.application;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.html.SafeHtml;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * README do GitHub (Markdown) → HTML da bio.
 *
 * <p>Tabelas e riscado (GFM) são convertidos; links e imagens relativos do repositório viram URLs
 * absolutas (blob para links, raw para imagens), porque a bio não tem o contexto do repositório.
 * O resultado passa pelo mesmo SafeHtml da bio antes de ser gravado — o README é conteúdo externo.</p>
 */
@Component
public class MarkdownBioRenderer {

	private final Parser parser;
	private final HtmlRenderer renderer;

	public MarkdownBioRenderer() {
		List<Extension> extensions = List.of(TablesExtension.create(), StrikethroughExtension.create());
		this.parser = Parser.builder().extensions(extensions).build();
		this.renderer = HtmlRenderer.builder().extensions(extensions).build();
	}

	public String render(String markdown, String owner, String repo, String branch) {
		Node document = parser.parse(markdown == null ? "" : markdown);
		Document html = Jsoup.parseBodyFragment(renderer.render(document));
		String blobBase = "https://github.com/" + owner + "/" + repo + "/blob/" + branch + "/";
		String rawBase = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/";
		for (Element link : html.select("a[href]")) {
			link.attr("href", absolute(link.attr("href"), blobBase));
		}
		for (Element image : html.select("img[src]")) {
			image.attr("src", absolute(image.attr("src"), rawBase));
		}
		try {
			return SafeHtml.requireProfileHtml(html.body().html());
		}
		catch (ApiException tooLarge) {
			if ("PROFILE_HTML_TOO_LONG".equals(tooLarge.code())) {
				throw new ApiException(400, "README_TOO_LONG",
					"O README é grande demais para a bio. Diminua o conteúdo ou cole um trecho.");
			}
			throw tooLarge;
		}
	}

	private static String absolute(String value, String base) {
		String url = value == null ? "" : value.trim();
		if (url.isEmpty() || url.startsWith("#") || url.startsWith("http://") || url.startsWith("https://")
				|| url.startsWith("//") || url.startsWith("mailto:") || url.startsWith("data:")) {
			return url;
		}
		if (url.startsWith("/")) {
			return base + url.substring(1);
		}
		return base + (url.startsWith("./") ? url.substring(2) : url);
	}
}
