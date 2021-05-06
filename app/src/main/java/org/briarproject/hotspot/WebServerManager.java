package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;
import java.util.logging.Logger;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.WebServerManager.WebServerState.ERROR;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STARTED;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STOPPED;

class WebServerManager {

	private static final Logger LOG =
			getLogger(WebServerManager.class.getName());

	enum WebServerState {STOPPED, STARTED, ERROR}

	private final WebServer webServer;

	private final MutableLiveData<WebServerState> webServerState =
			new MutableLiveData<>(STOPPED);

	WebServerManager(Context ctx) {
		webServer = new WebServer(ctx);
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerState;
	}

	void startWebServer() {
		try {
			webServer.start();
			webServerState.postValue(STARTED);
		} catch (IOException e) {
			LogUtils.logException(LOG, WARNING, e);
			webServerState.postValue(ERROR);
		}
	}

	void stopWebServer() {
		webServer.stop();
		webServerState.postValue(STOPPED);
	}

}
