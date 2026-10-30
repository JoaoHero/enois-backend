package com.socialcoisa.rede.mentoring.domain;

import java.time.Instant;
import java.util.List;

public class Mentor {

	private final String id;
	private final String userId;
	private final List<String> expertise;
	private final double rating;
	private final int sessionsCount;
	private final String price;
	private final String banner;
	private final String meetingUrl;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Mentor(String id, String userId, List<String> expertise, double rating, int sessionsCount, String price,
			String banner, String meetingUrl, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.userId = userId;
		this.expertise = expertise;
		this.rating = rating;
		this.sessionsCount = sessionsCount;
		this.price = price;
		this.banner = banner;
		this.meetingUrl = meetingUrl;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static Mentor create(String id, String userId, List<String> expertise, String price, Instant now) {
		return create(id, userId, expertise, price, null, null, now);
	}

	public static Mentor create(String id, String userId, List<String> expertise, String price, String banner,
			String meetingUrl, Instant now) {
		return new Mentor(id, userId, List.copyOf(expertise), 0.0, 0, price, banner, meetingUrl, now, now);
	}

	public static Mentor restore(String id, String userId, List<String> expertise, double rating, int sessionsCount,
			String price, Instant createdAt, Instant updatedAt) {
		return restore(id, userId, expertise, rating, sessionsCount, price, null, null, createdAt, updatedAt);
	}

	public static Mentor restore(String id, String userId, List<String> expertise, double rating, int sessionsCount,
			String price, String banner, String meetingUrl, Instant createdAt, Instant updatedAt) {
		return new Mentor(id, userId, expertise, rating, sessionsCount, price, banner, meetingUrl, createdAt,
			updatedAt);
	}

	public Mentor update(List<String> expertise, String price, Instant now) {
		return update(expertise, price, banner, meetingUrl, now);
	}

	public Mentor update(List<String> expertise, String price, String banner, String meetingUrl, Instant now) {
		return new Mentor(id, userId, expertise, rating, sessionsCount, price, banner, meetingUrl, createdAt, now);
	}

	public String meetingUrl() { return meetingUrl; }

	public String id() { return id; }

	public String userId() { return userId; }

	public List<String> expertise() { return expertise; }

	public double rating() { return rating; }

	public int sessionsCount() { return sessionsCount; }

	public String price() { return price; }

	public String banner() { return banner; }

	public Instant createdAt() { return createdAt; }

	public Instant updatedAt() { return updatedAt; }

}
