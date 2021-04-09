package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.app.Application;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;

import static android.content.Context.WIFI_P2P_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;
import static android.os.Build.VERSION.SDK_INT;

public class MainViewModel extends AndroidViewModel {

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;

	private final MutableLiveData<NetworkConfig> config = new MutableLiveData<>();
	private final MutableLiveData<String> status = new MutableLiveData<>();
	private final MutableLiveData<WebServerState> webServerState =
            new MutableLiveData<>(WebServerState.STOPPED);

	private final Application app;
	private final String lockTag;
	private final WifiManager wifiManager;
	private final WifiP2pManager wifiP2pManager;
	private final Handler handler;
	private final WebServer webServer;

	private WifiLock wifiLock;
	private Channel channel;

    public MainViewModel(@NonNull Application application) {
        super(application);
        app = application;
        lockTag = app.getString(R.string.app_name);
        wifiManager = (WifiManager) app.getSystemService(WIFI_SERVICE);
        wifiP2pManager = (WifiP2pManager) app.getSystemService(WIFI_P2P_SERVICE);
        handler = new Handler(app.getMainLooper());
        webServer = new WebServer(app);
    }

	LiveData<NetworkConfig> getWifiConfiguration() {
		return config;
	}

	LiveData<String> getStatus() {
		return status;
	}

	LiveData<WebServerState> getWebServerState() {
		return webServerState;
	}

	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			status.setValue(app.getString(R.string.no_wifi_direct));
			return;
		}
		status.setValue(app.getString(R.string.starting_hotspot));
		channel = wifiP2pManager.initialize(app, app.getMainLooper(), null);
		if (channel == null) {
			status.setValue(app.getString(R.string.no_wifi_direct));
			return;
		}
		acquireLock();
		ActionListener listener = new ActionListener() {

			@Override
			public void onSuccess() {
				status.setValue(app.getString(R.string.callback_waiting));
				requestGroupInfo(1);
			}

			@Override
			public void onFailure(int reason) {
				if (reason == 2) requestGroupInfo(1); // Hotspot already running
				else releaseWifiP2pHotspot(app.getString(R.string.callback_failed, reason));
			}
		};
		try {
			wifiP2pManager.createGroup(channel, listener);
		} catch (SecurityException e) {
			releaseWifiP2pHotspot(app.getString(R.string.callback_permission_denied));
		}
	}

	private void requestGroupInfo(int attempt) {
		GroupInfoListener listener = group -> {
			if (group == null) {
				// On some devices we need to wait for the group info to become available
				if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
					handler.postDelayed(() -> requestGroupInfo(attempt + 1), 1000);
				} else {
					releaseWifiP2pHotspot(app.getString(R.string.callback_no_group_info));
				}
			} else {
				config.setValue(new NetworkConfig(group.getNetworkName(), group.getPassphrase(),
						true));
				status.setValue(app.getString(R.string.callback_started));
                startWebServer();
			}
		};
		try {
			wifiP2pManager.requestGroupInfo(channel, listener);
		} catch (SecurityException e) {
			releaseWifiP2pHotspot(app.getString(R.string.callback_permission_denied));
		}
	}

	private void releaseWifiP2pHotspot(String statusMessage) {
        stopWebServer();
        if (SDK_INT >= 27) channel.close();
		channel = null;
		releaseLock();
		config.setValue(null);
		status.setValue(statusMessage);
	}

	void stopWifiP2pHotspot() {
		if (channel == null) return;
		wifiP2pManager.removeGroup(channel, new ActionListener() {

			@Override
			public void onSuccess() {
				releaseWifiP2pHotspot(app.getString(R.string.hotspot_stopped));
			}

			@Override
			public void onFailure(int reason) {
				releaseWifiP2pHotspot(app.getString(R.string.hotspot_stopped));
			}
		});
	}

	@Override
	protected void onCleared() {
		stopWifiP2pHotspot();
	}

	@SuppressLint("WakelockTimeout")
	private void acquireLock() {
		// WIFI_MODE_FULL has no effect on API >= 29
		int lockType = SDK_INT >= 29 ? WIFI_MODE_FULL_HIGH_PERF : WIFI_MODE_FULL;
		wifiLock = wifiManager.createWifiLock(lockType, lockTag);
		wifiLock.acquire();
	}

	private void releaseLock() {
		wifiLock.release();
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

    enum WebServerState { STOPPED, STARTED, ERROR }

	static class NetworkConfig {

		final String ssid, password;
		final boolean hidden;

		NetworkConfig(String ssid, String password, boolean hidden) {
			this.ssid = ssid;
			this.password = password;
			this.hidden = hidden;
		}
	}
}
