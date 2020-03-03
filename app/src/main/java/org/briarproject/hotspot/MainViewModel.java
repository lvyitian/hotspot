package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.app.Application;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import static android.content.Context.WIFI_P2P_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;
import static android.os.Build.VERSION.SDK_INT;

public class MainViewModel extends ViewModel {

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;

	private final MutableLiveData<NetworkConfig> config = new MutableLiveData<>();
	private final MutableLiveData<String> status = new MutableLiveData<>();

	private Application app;
	private String lockTag;
	private WifiManager wifiManager;
	private WifiP2pManager wifiP2pManager;
	private Handler handler;

	private WifiLock wifiLock;
	private LocalOnlyHotspotReservation reservation;
	private Channel channel;

	void setApplication(Application app) {
		this.app = app;
		lockTag = app.getString(R.string.app_name);
		wifiManager = (WifiManager) app.getSystemService(WIFI_SERVICE);
		wifiP2pManager = (WifiP2pManager) app.getSystemService(WIFI_P2P_SERVICE);
		handler = new Handler(app.getMainLooper());
	}

	LiveData<NetworkConfig> getWifiConfiguration() {
		return config;
	}

	LiveData<String> getStatus() {
		return status;
	}

	@RequiresApi(26)
	void startLocalOnlyHotspot() {
		if (wifiManager == null) {
			status.setValue(app.getString(R.string.no_wifi_manager));
			return;
		}
		status.setValue(app.getString(R.string.starting_hotspot));
		acquireLock();
		LocalOnlyHotspotCallback callback = new LocalOnlyHotspotCallback() {

			@Override
			public void onStarted(LocalOnlyHotspotReservation reservation) {
				MainViewModel.this.reservation = reservation;
				WifiConfiguration wifiConfig = reservation.getWifiConfiguration();
				config.setValue(new NetworkConfig(wifiConfig.SSID, wifiConfig.preSharedKey, false));
				status.setValue(app.getString(R.string.callback_started));
			}

			@Override
			public void onStopped() {
				releaseLocalOnlyHotspot(app.getString(R.string.callback_stopped));
			}

			@Override
			public void onFailed(int reason) {
				releaseLocalOnlyHotspot(app.getString(R.string.callback_failed, reason));
			}
		};
		try {
			wifiManager.startLocalOnlyHotspot(callback, null);
		} catch (SecurityException e) {
			releaseLocalOnlyHotspot(app.getString(R.string.enable_location_service));
		}
	}

	private void releaseLocalOnlyHotspot(String statusMessage) {
		reservation = null;
		releaseLock();
		config.setValue(null);
		status.setValue(statusMessage);
	}

	@RequiresApi(26)
	void stopLocalOnlyHotspot() {
		if (reservation == null) return;
		reservation.close();
		releaseLocalOnlyHotspot(app.getString(R.string.hotspot_stopped));
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
		wifiP2pManager.createGroup(channel, new ActionListener() {

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
		});
	}

	private void requestGroupInfo(int attempt) {
		wifiP2pManager.requestGroupInfo(channel, group -> {
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
			}
		});
	}

	private void releaseWifiP2pHotspot(String statusMessage) {
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
		if (SDK_INT >= 26) stopLocalOnlyHotspot();
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
