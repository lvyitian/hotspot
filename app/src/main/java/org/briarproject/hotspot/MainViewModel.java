package org.briarproject.hotspot;

import android.app.Application;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.hotspot.HotspotManager.HotspotListener;

public class MainViewModel extends AndroidViewModel implements HotspotListener {

	private final MutableLiveData<Boolean> is5GhzSupported =
			new MutableLiveData<>(false);
	private final MutableLiveData<WebServerState> webServerState =
			new MutableLiveData<>(WebServerState.STOPPED);

	private final HotspotManager hotSpotManager;
	private final WebServer webServer;

	public MainViewModel(@NonNull Application app) {
		super(app);
		hotSpotManager = new HotspotManager(app, this);
		webServer = new WebServer(app);

		if (SDK_INT >= 21) {
			WifiManager wifiManager =
					(WifiManager) app.getSystemService(WIFI_SERVICE);
			if (wifiManager.is5GHzBandSupported()) {
				is5GhzSupported.setValue(true);
			}
		}
	}

	LiveData<Boolean> getIs5GhzSupported() {
		return is5GhzSupported;
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerState;
	}

	HotspotManager getHotSpotManager() {
		return hotSpotManager;
	}

	void startWifiP2pHotspot() {
		hotSpotManager.startWifiP2pHotspot();
	}

	@Override
	protected void onCleared() {
		hotSpotManager.stopWifiP2pHotspot();
	}

	private void startWebServer() {
		try {
			webServer.start();
			webServerState.postValue(WebServerState.STARTED);
		} catch (IOException e) {
			e.printStackTrace();
			webServerState.postValue(WebServerState.ERROR);
		}
	}

	private void stopWebServer() {
		webServer.stop();
		webServerState.postValue(WebServerState.STOPPED);
	}

	@Override
	public void connected() {
		Log.e("TEST", "starting webserver");
		startWebServer();
	}

	@Override
	public void disconnected() {
		Log.e("TEST", "stopping webserver");
		stopWebServer();
	}

	enum WebServerState {STOPPED, STARTED, ERROR}

}
