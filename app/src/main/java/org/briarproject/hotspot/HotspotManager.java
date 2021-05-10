package org.briarproject.hotspot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;

import org.briarproject.hotspot.HotspotState.NetworkConfig;

import java.util.logging.Logger;

import androidx.annotation.Nullable;

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
import static org.briarproject.hotspot.StringUtils.getRandomString;

class HotspotManager {

	interface HotspotListener {

		void onWaitingToStartHotspot();

		void onStartingHotspot();

		void onHotspotStarted(NetworkConfig networkConfig);

		void onHotspotStopped(@Nullable String error);

	}

	private static final Logger LOG = getLogger(HotspotManager.class.getName());

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;

	static final double UNKNOWN_FREQUENCY = Double.NEGATIVE_INFINITY;

	private final Context ctx;
	private final HotspotListener listener;
	private final WifiManager wifiManager;
	private final WifiP2pManager wifiP2pManager;
	private final Handler handler;
	private final String lockTag;

	private WifiManager.WifiLock wifiLock;
	private WifiP2pManager.Channel channel;

	HotspotManager(Context ctx, HotspotListener listener) {
		this.ctx = ctx;
		this.listener = listener;
		wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		wifiP2pManager =
				(WifiP2pManager) ctx.getSystemService(WIFI_P2P_SERVICE);
		handler = new Handler(ctx.getMainLooper());
		lockTag = ctx.getString(R.string.app_name);
	}

	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			listener.onHotspotStopped(ctx.getString(R.string.no_wifi_direct));
			return;
		}
		listener.onStartingHotspot();
		channel = wifiP2pManager
				.initialize(ctx, ctx.getMainLooper(), null);
		if (channel == null) {
			listener.onHotspotStopped(ctx.getString(R.string.no_wifi_direct));
			return;
		}
		acquireLock();
		String networkName = getNetworkName();
		WifiP2pManager.ActionListener listener =
				new WifiP2pManager.ActionListener() {

					@Override
					public void onSuccess() {
						HotspotManager.this.listener.onWaitingToStartHotspot();
						requestGroupInfo(1, networkName);
					}

					@Override
					public void onFailure(int reason) {
						if (reason == BUSY) requestGroupInfo(1,
								networkName); // Hotspot already running
						else if (reason == P2P_UNSUPPORTED)
							releaseWifiP2pHotspot(
									ctx.getString(R.string.callback_failed,
											"p2p unsupported"));
						else if (reason == ERROR)
							releaseWifiP2pHotspot(
									ctx.getString(R.string.callback_failed,
											"p2p error"));
						else if (reason == NO_SERVICE_REQUESTS)
							releaseWifiP2pHotspot(ctx.getString(
									R.string.callback_failed,
									"no service requests"));
						else releaseWifiP2pHotspot(
									ctx.getString(R.string.callback_failed,
											"p2p error"));
						// all cases covered, in doubt set to error
					}
				};
		try {
			if (SDK_INT >= 29) {
				String passphrase = getPassphrase();
				// TODO: maybe remove this in the production version
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
			throw new AssertionError();
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
						releaseWifiP2pHotspot(null);
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

	private void releaseWifiP2pHotspot(@Nullable String error) {
		listener.onHotspotStopped(error);
		if (SDK_INT >= 27) channel.close();
		channel = null;
		releaseLock();
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
				HotspotManager.this.listener.onHotspotStarted(new NetworkConfig(
						group.getNetworkName(), group.getPassphrase(),
						frequency));
			} else {
				retryRequestingGroupInfo(attempt + 1, requestedNetworkName);
			}
		};
		try {
			wifiP2pManager.requestGroupInfo(channel, listener);
		} catch (SecurityException e) {
			throw new AssertionError();
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
			releaseWifiP2pHotspot(
					ctx.getString(R.string.callback_no_group_info));
		}
	}

}
