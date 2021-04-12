package org.briarproject.hotspot;

import java.security.SecureRandom;
import java.util.Random;

class StringUtils {

	private static final Random random = new SecureRandom();

	public static String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++) c[i] = (char) ('a' + random.nextInt(26));
		return new String(c);
	}

}
