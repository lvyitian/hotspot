package org.briarproject.hotspot;

import java.security.SecureRandom;
import java.util.Random;

class StringUtils {

	private static final Random random = new SecureRandom();

	private static String digits = "123456789"; // avoid 0
	private static String letters = "abcdefghijkmnopqrstuvwxyz"; // avoid l
	private static String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // avoid I, O

	public static String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++) {
			if (random.nextBoolean()) {
				c[i] = random(digits);
			} else if (random.nextBoolean()) {
				c[i] = random(letters);
			} else {
				c[i] = random(LETTERS);
			}
		}
		return new String(c);
	}

	private static char random(String universe) {
		return universe.charAt(random.nextInt(universe.length()));
	}

}
