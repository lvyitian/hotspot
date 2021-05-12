package org.briarproject.hotspot;

import android.app.Application;
import android.net.wifi.WifiManager;

import org.briarproject.hotspot.HotspotState.HotspotError;
import org.briarproject.hotspot.HotspotState.HotspotStarted;
import org.briarproject.hotspot.HotspotState.HotspotStopped;
import org.briarproject.hotspot.HotspotState.StartingHotspot;
import org.briarproject.hotspot.WebServerManager.WebServerState;

import java.util.logging.Logger;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.HotspotManager.HotspotListener;
import static org.briarproject.hotspot.WebServerManager.WebServerListener;

public class MainViewModel extends AndroidViewModel
		implements WebServerListener, HotspotListener {

	private static final Logger LOG = getLogger(MainViewModel.class.getName());

	private final MutableLiveData<Boolean> is5GhzSupported =
			new MutableLiveData<>();

	private final HotspotManager hotspotManager;
	private final WebServerManager webServerManager;

	private final MutableLiveData<HotspotState> status =
			new MutableLiveData<>();
	private HotspotState.NetworkConfig networkConfig;

	public MainViewModel(@NonNull Application app) {
		super(app);
		hotspotManager = new HotspotManager(app, this);
		webServerManager = new WebServerManager(app, this);

		if (SDK_INT >= 21) {
			WifiManager wifiManager =
					(WifiManager) app.getSystemService(WIFI_SERVICE);
			if (wifiManager.is5GHzBandSupported()) {
				is5GhzSupported.setValue(true);
			}
		}
	}

	LiveData<HotspotState> getStatus() {
		return status;
	}

	LiveData<Boolean> getIs5GhzSupported() {
		return is5GhzSupported;
	}

	HotspotManager getHotspotManager() {
		return hotspotManager;
	}

	void startWifiP2pHotspot() {
		hotspotManager.startWifiP2pHotspot();
	}

	@Override
	protected void onCleared() {
		// TODO: remove from managers?
		hotspotManager.stopWifiP2pHotspot();
		webServerManager.stopWebServer();
	}

	@Override
	public void onStartingHotspot() {
		status.setValue(new StartingHotspot());
	}

	@Override
	public void onHotspotStarted(HotspotState.NetworkConfig networkConfig) {
		this.networkConfig = networkConfig;
		LOG.info("starting webserver");
		webServerManager.startWebServer();
	}

	@Override
	public void onHotspotStopped() {
		status.setValue(new HotspotStopped());
		LOG.info("stopping webserver");
		webServerManager.stopWebServer();
	}

	@Override
	public void onHotspotError(String error) {
		status.setValue(new HotspotError(error));
	}

	@Override
	public void onStateChanged(WebServerState webServerStatus) {
		switch (webServerStatus) {
			case STARTED:
				status.postValue(new HotspotStarted(networkConfig));
				break;
			case STOPPED:
				// nothing to do in this case
				break;
			case ERROR:
				status.postValue(new HotspotError(
						getApplication().getString(R.string.web_server_error)));
				break;
		}
	}

}
