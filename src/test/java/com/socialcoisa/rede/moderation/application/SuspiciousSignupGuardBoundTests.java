package com.socialcoisa.rede.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.shared.web.ApiException;

/** The per-IP strike and cooldown maps stay bounded however many distinct IPs show up. */
class SuspiciousSignupGuardBoundTests {

	@Test
	void manyDistinctIpsInCooldownDoNotGrowTheMapsPastTheCeiling() {
		SuspiciousSignupGuard guard = new SuspiciousSignupGuard(true, "probe", 10);
		for (int ip = 0; ip < 100; ip++) {
			for (int strike = 0; strike < 3; strike++) {
				String address = "198.51.100." + ip;
				assertThatThrownBy(() -> guard.checkSignup("probe-x", "a@b.test", address))
					.isInstanceOf(ApiException.class);
			}
			assertThat(guard.trackedKeys()).isLessThanOrEqualTo(20);
		}
	}
}
