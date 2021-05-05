package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;

import java.util.logging.Logger;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.content.Context.WIFI_P2P_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_BAND_2GHZ;
import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.HotspotManager.HotspotError.NO_GROUP_INFO;
import static org.briarproject.hotspot.HotspotManager.HotspotError.NO_WIFI_DIRECT;
import static org.briarproject.hotspot.HotspotManager.HotspotError.OTHER;
import static org.briarproject.hotspot.HotspotManager.HotspotError.P2P_ERROR;
import static org.briarproject.hotspot.HotspotManager.HotspotError.P2P_NO_SERVICE_REQUESTS;
import static org.briarproject.hotspot.HotspotManager.HotspotError.P2P_P2P_UNSUPPORTED;
import static org.briarproject.hotspot.HotspotManager.HotspotError.PERMISSION_DENIED;
import static org.briarproject.hotspot.HotspotManager.HotspotState.HOTSPOT_STARTED;
import static org.briarproject.hotspot.HotspotManager.HotspotState.HOTSPOT_STOPPED;
import static org.briarproject.hotspot.HotspotManager.HotspotState.STARTING_HOTSPOT;
import static org.briarproject.hotspot.HotspotManager.HotspotState.WAITING_TO_START_HOTSPOT;
import static org.briarproject.hotspot.StringUtils.getRandomString;

class HotspotManager {

	private static final Logger LOG = getLogger(HotspotManager.class.getName());

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;

	enum HotspotState {
		STARTING_HOTSPOT,
		WAITING_TO_START_HOTSPOT,
		HOTSPOT_STARTED,
		HOTSPOT_STOPPED
	}

	enum HotspotError {
		NO_WIFI_DIRECT,
		P2P_ERROR,
		P2P_P2P_UNSUPPORTED,
		P2P_NO_SERVICE_REQUESTS,
		PERMISSION_DENIED,
		NO_GROUP_INFO,
		OTHER
	}

	static final double UNKNOWN_FREQUENCY = Double.NEGATIVE_INFINITY;

	private final Context context;
	private final WifiManager wifiManager;
	private final WifiP2pManager wifiP2pManager;
	private final Handler handler;
	private final String lockTag;

	private final MutableLiveData<NetworkConfig> config =
			new MutableLiveData<>();
	private final MutableLiveData<HotspotState> status =
			new MutableLiveData<>();
	private final MutableLiveData<HotspotError> error =
			new MutableLiveData<>();
	private final MutableLiveData<Double> frequency = new MutableLiveData<>();

	private WifiManager.WifiLock wifiLock;
	private WifiP2pManager.Channel channel;

	HotspotManager(Context context) {
		this.context = context;
		wifiManager = (WifiManager) context.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		wifiP2pManager =
				(WifiP2pManager) context.getSystemService(WIFI_P2P_SERVICE);
		handler = new Handler(context.getMainLooper());
		lockTag = context.getString(R.string.app_name);
	}

	LiveData<HotspotState> getStatus() {
		return status;
	}

	LiveData<HotspotError> getError() {
		return error;
	}

	LiveData<Double> getFrequency() {
		return frequency;
	}

	LiveData<NetworkConfig> getWifiConfiguration() {
		return config;
	}

	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			error.setValue(NO_WIFI_DIRECT);
			return;
		}
		status.setValue(STARTING_HOTSPOT);
		channel = wifiP2pManager
				.initialize(context, context.getMainLooper(), null);
		if (channel == null) {
			error.setValue(NO_WIFI_DIRECT);
			return;
		}
		acquireLock();
		String networkName =
				SDK_INT >= 29 ? "DIRECT-" + getRandomString(2) + "-" +
						getRandomString(10) : null;
		WifiP2pManager.ActionListener listener =
				new WifiP2pManager.ActionListener() {

					@Override
					public void onSuccess() {
						status.setValue(WAITING_TO_START_HOTSPOT);
						requestGroupInfo(1, networkName);
					}

					@Override
					public void onFailure(int reason) {
						if (reason == BUSY) requestGroupInfo(1,
								networkName); // Hotspot already running
						else if (reason == P2P_UNSUPPORTED)
							releaseWifiP2pHotspot(P2P_P2P_UNSUPPORTED);
						else if (reason == ERROR)
							releaseWifiP2pHotspot(P2P_ERROR);
						else if (reason == NO_SERVICE_REQUESTS)
							releaseWifiP2pHotspot(P2P_NO_SERVICE_REQUESTS);
						else releaseWifiP2pHotspot(P2P_ERROR);
						// all cases covered, in doubt set to error
					}
				};
		try {
			if (SDK_INT >= 29) {
				String passphrase = getRandomString(8);
				LOG.info("networkName: " + networkName);
				LOG.info("passphrase: " + passphrase);
				WifiP2pConfig config = new WifiP2pConfig.Builder()
						.setGroupOperatingBand(GROUP_OWNER_BAND_2GHZ)
						.setNetworkName(networkName)
						.setPassphrase(passphrase)
						.build();
				wifiP2pManager.createGroup(channel, config, listener);
			} else {
				wifiP2pManager.createGroup(channel, listener);
			}
		} catch (SecurityException e) {
			releaseWifiP2pHotspot(PERMISSION_DENIED);
		}
	}

	void stopWifiP2pHotspot() {
		if (channel == null) return;
		wifiP2pManager
				.removeGroup(channel, new WifiP2pManager.ActionListener() {

					@Override
					public void onSuccess() {
						releaseWifiP2pHotspot(null);
					}

					@Override
					public void onFailure(int reason) {
						releaseWifiP2pHotspot(OTHER);
					}
				});
	}

	@SuppressLint("WakelockTimeout")
	private void acquireLock() {
		// WIFI_MODE_FULL has no effect on API >= 29
		int lockType =
				SDK_INT >= 29 ? WIFI_MODE_FULL_HIGH_PERF : WIFI_MODE_FULL;
		wifiLock = wifiManager.createWifiLock(lockType, lockTag);
		wifiLock.acquire();
	}

	private void releaseLock() {
		wifiLock.release();
	}

	private void releaseWifiP2pHotspot(HotspotError error) {
		status.setValue(HOTSPOT_STOPPED);
		if (SDK_INT >= 27) channel.close();
		channel = null;
		releaseLock();
		config.setValue(null);
		this.error.setValue(error);
	}

	private void requestGroupInfo(int attempt, @Nullable String networkName) {
		LOG.info("requestGroupInfo attempt: " + attempt);
		WifiP2pManager.GroupInfoListener listener = group -> {
			boolean retry = false;
			if (group == null) {
				LOG.info("group is null");
				retry = true;
			} else if (!group.getNetworkName().startsWith("DIRECT-") ||
					(networkName != null &&
							!networkName.equals(group.getNetworkName()))) {
				// we only retry if we have attempts left, otherwise we try what we got
				if (attempt < MAX_GROUP_INFO_ATTEMPTS) retry = true;
				LOG.info("expected networkName: " + networkName);
				LOG.info(
						"received networkName: " + group.getNetworkName());
				LOG.info("received passphrase: " + group.getPassphrase());
			}
			if (retry) {
				LOG.info("retrying");
				// On some devices we need to wait for the group info to become available
				if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
					handler.postDelayed(
							() -> requestGroupInfo(attempt + 1, networkName),
							1000);
				} else {
					releaseWifiP2pHotspot(NO_GROUP_INFO);
				}
				return;
			}
			config.setValue(new NetworkConfig(group.getNetworkName(),
					group.getPassphrase(), true));
			if (SDK_INT >= 29) {
				frequency.setValue(((double) group.getFrequency()) / 1000);
			} else {
				frequency.setValue(UNKNOWN_FREQUENCY);
			}
			status.setValue(HOTSPOT_STARTED);
		};
		try {
			wifiP2pManager.requestGroupInfo(channel, listener);
		} catch (SecurityException e) {
			releaseWifiP2pHotspot(PERMISSION_DENIED);
		}
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
