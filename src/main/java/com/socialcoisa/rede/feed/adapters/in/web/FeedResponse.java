package com.socialcoisa.rede.feed.adapters.in.web;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.feed.application.FeedPage;
import com.socialcoisa.rede.feed.application.PostView;

/**
 * GET /feed body: the page plus {@code lastSeenAt}, the person's "voce esta em dia" marker as it was
 * BEFORE this visit. Only the first page (no cursor) carries it; cursor pages send null and the front
 * keeps the value it got on the first page.
 */
record FeedResponse(List<PostView> items, String nextCursor, Instant lastSeenAt) {

	static FeedResponse of(FeedPage page, Instant lastSeenAt) {
		return new FeedResponse(page.items(), page.nextCursor(), lastSeenAt);
	}
}
