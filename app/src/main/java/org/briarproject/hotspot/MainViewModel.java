package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.app.Application;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation;
import android.net.wifi.WifiManager.WifiLock;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("deprecation")
public class MainViewModel extends ViewModel {

	private final MutableLiveData<WifiConfiguration> config = new MutableLiveData<>();
	private final MutableLiveData<String> status = new MutableLiveData<>();

	private Application app;
	private String lockTag;
	private WifiManager wifiManager;
	private PowerManager powerManager;
	private WifiLock wifiLock;
	private WakeLock wakeLock;
	private LocalOnlyHotspotReservation reservation;

	void setApplication(Application app) {
		this.app = app;
		lockTag = app.getString(R.string.app_name);
		wifiManager = (WifiManager) app.getSystemService(WIFI_SERVICE);
		powerManager = (PowerManager) requireNonNull(app.getSystemService(POWER_SERVICE));
	}

	LiveData<WifiConfiguration> getWifiConfiguration() {
		return config;
	}

	LiveData<String> getStatus() {
		return status;
	}

	@RequiresApi(26)
	void startHotspot() {
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
				config.setValue(reservation.getWifiConfiguration());
				status.setValue(app.getString(R.string.callback_started));
			}

			@Override
			public void onStopped() {
				reservation = null;
				releaseLock();
				config.setValue(null);
				status.setValue(app.getString(R.string.callback_stopped));
			}

			@Override
			public void onFailed(int reason) {
				reservation = null;
				releaseLock();
				config.setValue(null);
				status.setValue(app.getString(R.string.callback_failed, reason));
			}
		};
		try {
			wifiManager.startLocalOnlyHotspot(callback, null);
		} catch (SecurityException e) {
			releaseLock();
			status.setValue(app.getString(R.string.enable_location_service));
		}
	}

	@SuppressLint("WakelockTimeout")
	private void acquireLock() {
		if (SDK_INT >= 29) {
			// WIFI_MODE_FULL has no effect on API >= 29 so we have to hold a wake lock
			wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, lockTag);
			wakeLock.acquire();
		} else {
			wifiLock = wifiManager.createWifiLock(WIFI_MODE_FULL, lockTag);
			wifiLock.acquire();
		}

	}

	private void releaseLock() {
		if (SDK_INT >= 29) wakeLock.release();
		else wifiLock.release();
	}

	@RequiresApi(26)
	void stopHotspot() {
		if (reservation != null) {
			reservation.close();
			reservation = null;
			releaseLock();
			config.setValue(null);
			status.setValue(app.getString(R.string.hotspot_stopped));
		}
	}

	@Override
	protected void onCleared() {
		if (SDK_INT >= 26 && reservation != null) {
			reservation.close();
			reservation = null;
			releaseLock();
			config.setValue(null);
			status.setValue(app.getString(R.string.hotspot_stopped));
		}
	}
}
