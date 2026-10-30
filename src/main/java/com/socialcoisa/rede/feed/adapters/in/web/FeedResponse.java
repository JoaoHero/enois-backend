package com.socialcoisa.rede.feed.adapters.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.socialcoisa.rede.feed.application.FeedPage;
import com.socialcoisa.rede.feed.application.PostView;
import com.socialcoisa.rede.feed.domain.FeedTab;

/**
 * GET /feed body: the page plus {@code lastSeenAt}, the person's "voce esta em dia" marker as it was
 * BEFORE this visit. Only the first page (no cursor) carries it; cursor pages send null and the front
 * keeps the value it got on the first page.
 *
 * <p>The front draws the divider before the first returned post with {@code createdAt <= lastSeenAt},
 * which only makes sense when the page is newest-first. FOR_YOU (preferred topics first), TRENDING
 * (by reactions) and INTERACTED (posts the person touched) are not, so they always send null.
 */
record FeedResponse(List<PostView> items, String nextCursor, Instant lastSeenAt) {

	private static final Set<FeedTab> RANKED_TABS = Set.of(FeedTab.FOR_YOU, FeedTab.TRENDING, FeedTab.INTERACTED);

	static boolean carriesMarker(FeedTab tab) {
		return tab != null && !RANKED_TABS.contains(tab);
	}

	static FeedResponse of(FeedPage page, Instant lastSeenAt) {
		return new FeedResponse(page.items(), page.nextCursor(), lastSeenAt);
	}
}
