package org.briarproject.hotspot;

import android.app.Application;
import android.net.wifi.WifiManager;

import org.briarproject.hotspot.HotspotState.HotspotStarted;
import org.briarproject.hotspot.HotspotState.HotspotStopped;
import org.briarproject.hotspot.WebServerManager.WebServerState;

import java.util.logging.Logger;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;

public class MainViewModel extends AndroidViewModel
		implements Observer<HotspotState> {

	private static final Logger LOG = getLogger(MainViewModel.class.getName());

	private final MutableLiveData<Boolean> is5GhzSupported =
			new MutableLiveData<>();

	private final HotspotManager hotspotManager;
	private final WebServerManager webServerManager;

	public MainViewModel(@NonNull Application app) {
		super(app);
		hotspotManager = new HotspotManager(app);
		webServerManager = new WebServerManager(app);

		if (SDK_INT >= 21) {
			WifiManager wifiManager =
					(WifiManager) app.getSystemService(WIFI_SERVICE);
			if (wifiManager.is5GHzBandSupported()) {
				is5GhzSupported.setValue(true);
			}
		}

		hotspotManager.getStatus().observeForever(this);
	}

	LiveData<Boolean> getIs5GhzSupported() {
		return is5GhzSupported;
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerManager.getWebServerState();
	}

	HotspotManager getHotspotManager() {
		return hotspotManager;
	}

	void startWifiP2pHotspot() {
		hotspotManager.startWifiP2pHotspot();
	}

	@Override
	protected void onCleared() {
		hotspotManager.getStatus().removeObserver(this);
		hotspotManager.stopWifiP2pHotspot();
		webServerManager.stopWebServer();
	}

	@Override
	public void onChanged(HotspotState state) {
		if (state instanceof HotspotStarted) {
			LOG.info("starting webserver");
			webServerManager.startWebServer();
		} else if (state instanceof HotspotStopped) {
			LOG.info("stopping webserver");
			webServerManager.stopWebServer();
		}
	}

}
