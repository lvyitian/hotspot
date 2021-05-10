package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

class WebServerManager {

	interface WebServerListener {

		void onWebServerStarted();

		void onWebServerStopped();

		void onWebServerError();

	}

	private static final Logger LOG =
			getLogger(WebServerManager.class.getName());

	private final WebServer webServer;
	private WebServerListener listener;

	WebServerManager(Context ctx, WebServerListener listener) {
		this.listener = listener;
		webServer = new WebServer(ctx);
	}

	void startWebServer() {
		try {
			webServer.start();
			listener.onWebServerStarted();
		} catch (IOException e) {
			LogUtils.logException(LOG, WARNING, e);
			listener.onWebServerError();
		}
	}

	void stopWebServer() {
		webServer.stop();
		listener.onWebServerStopped();
	}

}
