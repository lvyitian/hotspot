package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.LogUtils.logException;
import static org.briarproject.hotspot.WebServerManager.WebServerState.ERROR;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STARTED;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STOPPED;

class WebServerManager {

	enum WebServerState {STOPPED, STARTED, ERROR}

	interface WebServerListener {

		void onStateChanged(WebServerState status);

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
				listener.onStateChanged(ERROR);
			}
			listener.onStateChanged(STARTED);
		}).start();
	}

	void stopWebServer() {
		webServer.stop();
		listener.onStateChanged(STOPPED);
	}

}
