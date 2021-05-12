package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;
import java.util.logging.Logger;

import androidx.annotation.UiThread;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.LogUtils.logException;

class WebServerManager {

	interface WebServerListener {

		void onWebServerStarted();

		@UiThread
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
		// TODO: offload this to the IoExecutor
		new Thread(() -> {
			try {
				webServer.start();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				listener.onWebServerError();
			}
			listener.onWebServerStarted();
		}).start();
	}

	void stopWebServer() {
		webServer.stop();
		listener.onWebServerStopped();
	}

}
