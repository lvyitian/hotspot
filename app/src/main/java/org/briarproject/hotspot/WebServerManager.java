package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

import androidx.annotation.WorkerThread;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.LogUtils.logException;
import static org.briarproject.hotspot.NetworkUtils.getAccessPointAddress;
import static org.briarproject.hotspot.WebServer.PORT;

class WebServerManager {

	interface WebServerListener {
		@WorkerThread
		void onWebServerStarted(String url);

		@WorkerThread
		void onWebServerError();
	}

	private static final Logger LOG =
			getLogger(WebServerManager.class.getName());

	private final WebServer webServer;
	private final WebServerListener listener;

	WebServerManager(Context ctx, WebServerListener listener) {
		this.listener = listener;
		webServer = new WebServer(ctx);
	}

	@WorkerThread
	void startWebServer() {
		try {
			webServer.start();
			onWebServerStarted();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			listener.onWebServerError();
		}
	}

	private void onWebServerStarted() {
		String url = "http://192.168.49.1:" + PORT;
		InetAddress address = getAccessPointAddress();
		if (address == null) {
			LOG.info(
					"Could not find access point address, assuming 192.168.49.1");
		} else {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Access point address " + address.getHostAddress());
			}
			url = "http://" + address.getHostAddress() + ":" + PORT;
		}
		listener.onWebServerStarted(url);
	}

	/**
	 * It is safe to call this more than once and it won't throw.
	 */
	void stopWebServer() {
		webServer.stop();
	}

}
