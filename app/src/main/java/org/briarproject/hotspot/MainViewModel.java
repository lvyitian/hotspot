package org.briarproject.hotspot;

import android.app.Application;
import android.net.wifi.WifiManager;

import org.briarproject.hotspot.HotspotState.HotspotError;
import org.briarproject.hotspot.HotspotState.HotspotStarted;
import org.briarproject.hotspot.HotspotState.HotspotStopped;
import org.briarproject.hotspot.HotspotState.NetworkConfig;
import org.briarproject.hotspot.HotspotState.StartingHotspot;

import java.util.logging.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
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

	@UiThread
	void startWifiP2pHotspot() {
		hotspotManager.startWifiP2pHotspot();
	}

	@UiThread
	void stopWifiP2pHotspot() {
		// stop the webserver before the hotspot
		// TODO maybe off-load stopping to IoExecutor as it can block
		webServerManager.stopWebServer();
		hotspotManager.stopWifiP2pHotspot();
	}

	@Override
	protected void onCleared() {
		stopWifiP2pHotspot();
	}

	@Override
	public void onStartingHotspot() {
		status.setValue(new StartingHotspot());
	}

	@Nullable
	// Field to temporarily store the network config received via onHotspotStarted()
	// in order to post it along with a HotspotStarted status
	private volatile NetworkConfig networkConfig;

	@Override
	public void onHotspotStarted(NetworkConfig networkConfig) {
		this.networkConfig = networkConfig;
		LOG.info("starting webserver");
		// TODO: offload this to the IoExecutor
		webServerManager.startWebServer();
	}

	@Override
	public void onHotspotStopped() {
		status.setValue(new HotspotStopped());
		LOG.info("stopping webserver");
		// TODO maybe off-load stopping to IoExecutor as it can block
		webServerManager.stopWebServer();
	}

	@Override
	public void onHotspotError(String error) {
		status.setValue(new HotspotError(error));
		// TODO maybe off-load stopping to IoExecutor as it can block
		webServerManager.stopWebServer();
	}

	@Override
	@WorkerThread
	public void onWebServerStarted(String url) {
		status.postValue(new HotspotStarted(networkConfig, url));
		networkConfig = null;
	}

	@Override
	@WorkerThread
	public void onWebServerError() {
		status.postValue(new HotspotError(
				getApplication().getString(R.string.web_server_error)));
	}

}
