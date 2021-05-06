package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;

import org.briarproject.hotspot.HotspotState.HotspotError;
import org.briarproject.hotspot.HotspotState.HotspotStarted;
import org.briarproject.hotspot.HotspotState.HotspotStopped;
import org.briarproject.hotspot.HotspotState.NetworkConfig;
import org.briarproject.hotspot.HotspotState.StartingHotspot;
import org.briarproject.hotspot.HotspotState.WaitingToStartHotspot;

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
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.HotspotState.HotspotError.NO_GROUP_INFO;
import static org.briarproject.hotspot.HotspotState.HotspotError.NO_WIFI_DIRECT;
import static org.briarproject.hotspot.HotspotState.HotspotError.OTHER;
import static org.briarproject.hotspot.HotspotState.HotspotError.P2P_ERROR;
import static org.briarproject.hotspot.HotspotState.HotspotError.P2P_NO_SERVICE_REQUESTS;
import static org.briarproject.hotspot.HotspotState.HotspotError.P2P_P2P_UNSUPPORTED;
import static org.briarproject.hotspot.HotspotState.HotspotError.PERMISSION_DENIED;
import static org.briarproject.hotspot.StringUtils.getRandomString;

class HotspotManager {

	private static final Logger LOG = getLogger(HotspotManager.class.getName());

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;

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

	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			status.setValue(new HotspotStopped(NO_WIFI_DIRECT));
			return;
		}
		status.setValue(new StartingHotspot());
		channel = wifiP2pManager
				.initialize(context, context.getMainLooper(), null);
		if (channel == null) {
			status.setValue(new HotspotStopped(NO_WIFI_DIRECT));
			return;
		}
		acquireLock();
		String networkName = getNetworkName();
		WifiP2pManager.ActionListener listener =
				new WifiP2pManager.ActionListener() {

					@Override
					public void onSuccess() {
						status.setValue(new WaitingToStartHotspot());
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
				String passphrase = getPassphrase();
				LOG.info("networkName: " + networkName);
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

	@Nullable
	private String getNetworkName() {
		return SDK_INT >= 29 ? "DIRECT-" + getRandomString(2) + "-" +
				getRandomString(10) : null;
	}

	private String getPassphrase() {
		return getRandomString(8);
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

	private void releaseWifiP2pHotspot(@Nullable HotspotError error) {
		status.setValue(new HotspotStopped(error));
		if (SDK_INT >= 27) channel.close();
		channel = null;
		releaseLock();
		config.setValue(null);
	}

	private void requestGroupInfo(int attempt,
			@Nullable String requestedNetworkName) {
		if (LOG.isLoggable(INFO))
			LOG.info("requestGroupInfo attempt: " + attempt);

		WifiP2pManager.GroupInfoListener listener = group -> {
			boolean valid = isGroupValid(group, requestedNetworkName);
			// If the group is valid, set the hotspot to started. If we don't
			// have any attempts left, we try what we got
			if (valid || attempt >= MAX_GROUP_INFO_ATTEMPTS) {
				double frequency = UNKNOWN_FREQUENCY;
				if (SDK_INT >= 29) {
					frequency = ((double) group.getFrequency()) / 1000;
				}
				status.setValue(new HotspotStarted(new NetworkConfig(
						group.getNetworkName(), group.getPassphrase(),
						frequency)));
			} else {
				retryRequestingGroupInfo(attempt + 1, requestedNetworkName);
			}
		};
		try {
			wifiP2pManager.requestGroupInfo(channel, listener);
		} catch (SecurityException e) {
			releaseWifiP2pHotspot(PERMISSION_DENIED);
		}
	}

	private boolean isGroupValid(WifiP2pGroup group,
			String requestedNetworkName) {
		if (group == null) {
			LOG.info("group is null");
			return false;
		} else if (!group.getNetworkName().startsWith("DIRECT-")) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("received networkName without prefix 'DIRECT-': " +
						group.getNetworkName());
			}
			return false;
		} else if (requestedNetworkName != null && !requestedNetworkName
				.equals(group.getNetworkName())) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("expected networkName: " + requestedNetworkName);
				LOG.info("received networkName: " + group.getNetworkName());
			}
			return false;
		}
		return true;
	}

	private void retryRequestingGroupInfo(int attempt,
			String requestedNetworkName) {
		LOG.info("retrying");
		// On some devices we need to wait for the group info to become available
		if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
			handler.postDelayed(() -> requestGroupInfo(attempt + 1,
					requestedNetworkName), 1000);
		} else {
			releaseWifiP2pHotspot(NO_GROUP_INFO);
		}
	}

}
