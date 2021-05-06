package org.briarproject.hotspot;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogUtils {

	public static void logException(Logger logger, Level level, Throwable t) {
		if (logger.isLoggable(level)) logger.log(level, t.toString(), t);
	}

}
