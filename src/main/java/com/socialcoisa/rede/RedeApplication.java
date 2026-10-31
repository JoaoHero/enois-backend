package com.socialcoisa.rede;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RedeApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(RedeApplication.class);
		app.addListeners((ApplicationEnvironmentPreparedEvent event) -> requireExplicitProfile(event.getEnvironment()));
		app.run(args);
	}

	/**
	 * The dev profile relaxes throttles and uses an in-memory database, so it must never be picked
	 * by accident: a deploy without SPRING_PROFILES_ACTIVE fails instead of booting as dev.
	 */
	static void requireExplicitProfile(Environment environment) {
		if (environment.getActiveProfiles().length == 0) {
			throw new IllegalStateException(
					"No Spring profile set: define SPRING_PROFILES_ACTIVE (dev, qa, hml or prd)");
		}
	}

}
