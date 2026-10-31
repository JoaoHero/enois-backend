package com.socialcoisa.rede.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS headers for responses that filters write before Spring MVC's CORS handling runs (429, CSRF
 * 403). Without them the browser hides the status behind a network error. The origin gate is the
 * only place that decides trust; it marks the request, and only a marked origin is reflected here
 * with credentials, so an arbitrary site never gets a credentialed CORS grant.
 */
public final class TrustedOriginCors {

	private static final String ATTRIBUTE = TrustedOriginCors.class.getName() + ".origin";

	private TrustedOriginCors() {
	}

	/** Called by the origin gate once the request's Origin is one of the app's own. */
	public static void markTrusted(HttpServletRequest request, String origin) {
		request.setAttribute(ATTRIBUTE, origin);
	}

	/** Reflects the origin with credentials when the origin gate marked it trusted; otherwise nothing. */
	public static void apply(HttpServletRequest request, HttpServletResponse response) {
		if (request.getAttribute(ATTRIBUTE) instanceof String origin) {
			response.setHeader("Access-Control-Allow-Origin", origin);
			response.setHeader("Access-Control-Allow-Credentials", "true");
			response.addHeader("Vary", "Origin");
		}
	}
}
