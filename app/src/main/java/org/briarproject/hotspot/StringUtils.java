package org.briarproject.hotspot;

import java.security.SecureRandom;
import java.util.Random;

class StringUtils {

	private static final Random random = new SecureRandom();

	public static String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++) {
			if (random.nextBoolean()) {
				c[i] = (char) ('0' + random.nextInt(10));
			} else if (random.nextBoolean()) {
				c[i] = (char) ('a' + random.nextInt(26));
			} else {
				c[i] = (char) ('A' + random.nextInt(26));
			}
		}
		return new String(c);
	}

}
