package org.briarproject.hotspot;

import android.content.Context;

import java.io.IOException;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static org.briarproject.hotspot.WebServerManager.WebServerState.ERROR;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STARTED;
import static org.briarproject.hotspot.WebServerManager.WebServerState.STOPPED;

class WebServerManager {

	enum WebServerState {STOPPED, STARTED, ERROR}

	private final WebServer webServer;

	private final MutableLiveData<WebServerState> webServerState =
			new MutableLiveData<>(STOPPED);

	WebServerManager(Context context) {
		webServer = new WebServer(context);
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerState;
	}

	void startWebServer() {
		try {
			webServer.start();
			webServerState.postValue(STARTED);
		} catch (IOException e) {
			e.printStackTrace();
			webServerState.postValue(ERROR);
		}
	}

	void stopWebServer() {
		webServer.stop();
		webServerState.postValue(STOPPED);
	}

}
