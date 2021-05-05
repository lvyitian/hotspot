package org.briarproject.hotspot;

import android.app.Application;
import android.net.wifi.WifiManager;

import org.briarproject.hotspot.HotspotManager.HotspotState;
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
			new MutableLiveData<>(false);

	private final HotspotManager hotSpotManager;
	private final WebServerManager webServerManager;

	public MainViewModel(@NonNull Application app) {
		super(app);
		hotSpotManager = new HotspotManager(app);
		webServerManager = new WebServerManager(app);

		if (SDK_INT >= 21) {
			WifiManager wifiManager =
					(WifiManager) app.getSystemService(WIFI_SERVICE);
			if (wifiManager.is5GHzBandSupported()) {
				is5GhzSupported.setValue(true);
			}
		}

		hotSpotManager.getStatus().observeForever(this);
	}

	LiveData<Boolean> getIs5GhzSupported() {
		return is5GhzSupported;
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerManager.getWebServerState();
	}

	HotspotManager getHotSpotManager() {
		return hotSpotManager;
	}

	void startWifiP2pHotspot() {
		hotSpotManager.startWifiP2pHotspot();
	}

	@Override
	protected void onCleared() {
		hotSpotManager.getStatus().removeObserver(this);
		hotSpotManager.stopWifiP2pHotspot();
	}

	@Override
	public void onChanged(HotspotState state) {
		switch (state) {
			case HOTSPOT_STARTED:
				LOG.info("starting webserver");
				webServerManager.startWebServer();
				break;
			case HOTSPOT_STOPPED:
				LOG.info("stopping webserver");
				webServerManager.stopWebServer();
				break;
		}
	}

}
