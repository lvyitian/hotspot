package org.briarproject.hotspot;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.os.Handler;

import org.briarproject.hotspot.HotspotState.NetworkConfig;

import java.util.logging.Logger;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

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

class HotspotManager implements ActionListener {

	interface HotspotListener {

		void onStartingHotspot();

		void onHotspotStarted(NetworkConfig networkConfig);

		void onDeviceConnected();

		void onHotspotStopped();

		void onHotspotError(String error);

	}

	private static final Logger LOG = getLogger(HotspotManager.class.getName());


	private static final int MAX_FRAMEWORK_ATTEMPTS = 5;
	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;
	private static final int RETRY_DELAY_MILLIS = 1000;

	static final double UNKNOWN_FREQUENCY = Double.NEGATIVE_INFINITY;

	private final Context ctx;
	private final HotspotListener listener;
	private final WifiManager wifiManager;
	private final WifiP2pManager wifiP2pManager;
	private final Handler handler;
	private final String lockTag;
	/**
	 * As soon as Wifi is enabled, we try starting the WifiP2p framework.
	 * If Wifi has just been enabled, it is possible that fails. If that happens
	 * we try again for MAX_FRAMEWORK_ATTEMPTS times after a delay of
	 * RETRY_DELAY_MILLIS after each attempt.
	 * <p>
	 * Rationale: it can take a few milliseconds for WifiP2p to become available
	 * after enabling Wifi. Depending on the API level it is possible to check this
	 * using {@link WifiP2pManager#requestP2pState} or register a BroadcastReceiver
	 * on the WIFI_P2P_STATE_CHANGED_ACTION to get notified when WifiP2p is really
	 * available. Trying to implement a solution that works reliably using these
	 * checks turned out to be a long rabbit-hole with lots of corner cases and
	 * workarounds for specific situations.
	 * Instead we now rely on this trial-and-error approach of just starting
	 * the framework and retrying if it fails.
	 * <p>
	 * We'll realize that the framework is busy when the ActionListener passed
	 * to {@link WifiP2pManager#createGroup} is called with onFailure(BUSY)
	 */
	private int attemptToStartFramework;

	@Nullable
	// on API < 29 this is null because we cannot request a custom network name
	private String networkName = null;

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
		lockTag = ctx.getPackageName() + ":app-sharing-hotspot";
	}

	@UiThread
	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			listener.onHotspotError(ctx.getString(R.string.no_wifi_direct));
			return;
		}
		listener.onStartingHotspot();
		acquireLock();
		attemptToStartFramework = 1;
		startWifiP2pFramework();
	}

	void startWifiP2pFramework() {
		/**
		 * It is important that we call {@link WifiP2pManager#initialize} again
		 * for every attempt to starting the framework because otherwise,
		 * createGroup() will continue to fail with a BUSY state.
		 */
		channel = wifiP2pManager.initialize(ctx, ctx.getMainLooper(), null);
		if (channel == null) {
			listener.onHotspotError(ctx.getString(R.string.no_wifi_direct));
			return;
		}
		try {
			if (SDK_INT >= 29) {
				networkName = getNetworkName();
				String passphrase = getPassphrase();
				// TODO: maybe remove this in the production version
				if (LOG.isLoggable(INFO))
					LOG.info("networkName: " + networkName);
				WifiP2pConfig config = new WifiP2pConfig.Builder()
						.setGroupOperatingBand(GROUP_OWNER_BAND_2GHZ)
						.setNetworkName(networkName)
						.setPassphrase(passphrase)
						.build();
				wifiP2pManager.createGroup(channel, config, this);
			} else {
				wifiP2pManager.createGroup(channel, this);
			}
		} catch (SecurityException e) {
			// this should never happen, because we request permissions before
			throw new AssertionError(e);
		}
	}

	private void restartWifiP2pFramework() {
		if (attemptToStartFramework++ < MAX_FRAMEWORK_ATTEMPTS) {
			handler.postDelayed(this::startWifiP2pFramework,
					RETRY_DELAY_MILLIS);
		} else {
			releaseHotspotWithError(
					ctx.getString(R.string.stop_framework_busy));
		}
	}

	@RequiresApi(29)
	private String getNetworkName() {
		return "DIRECT-" + getRandomString(2) + "-" +
				getRandomString(10);
	}

	private String getPassphrase() {
		return getRandomString(8);
	}

	@UiThread
	void stopWifiP2pHotspot() {
		if (channel == null) return;
		wifiP2pManager.removeGroup(channel, new ActionListener() {

			@Override
			public void onSuccess() {
				releaseHotspot();
			}

			@Override
			public void onFailure(int reason) {
				releaseHotspotWithError(ctx.getString(
						R.string.stop_callback_failed, reason));
			}

		});
	}

	private void acquireLock() {
		// WIFI_MODE_FULL has no effect on API >= 29
		int lockType =
				SDK_INT >= 29 ? WIFI_MODE_FULL_HIGH_PERF : WIFI_MODE_FULL;
		wifiLock = wifiManager.createWifiLock(lockType, lockTag);
		wifiLock.acquire();
	}

	private void releaseHotspot() {
		listener.onHotspotStopped();
		closeChannelAndReleaseLock();
	}

	private void releaseHotspotWithError(String error) {
		listener.onHotspotError(error);
		closeChannelAndReleaseLock();
	}

	private void closeChannelAndReleaseLock() {
		if (SDK_INT >= 27) channel.close();
		channel = null;
		wifiLock.release();
	}

	@Override
	// Callback for wifiP2pManager#createGroup() during startWifiP2pHotspot()
	public void onSuccess() {
		requestGroupInfo(1);
	}

	@Override
	// Callback for wifiP2pManager#createGroup() during startWifiP2pHotspot()
	public void onFailure(int reason) {
		LOG.info("onFailure: " + reason);
		if (reason == BUSY) {
			// WifiP2p not ready yet or hotspot already running
			restartWifiP2pFramework();
		} else if (reason == P2P_UNSUPPORTED) {
			releaseHotspotWithError(ctx.getString(
					R.string.start_callback_failed, "p2p unsupported"));
		} else if (reason == ERROR) {
			releaseHotspotWithError(ctx.getString(
					R.string.start_callback_failed, "p2p error"));
		} else if (reason == NO_SERVICE_REQUESTS) {
			releaseHotspotWithError(ctx.getString(
					R.string.start_callback_failed, "no service requests"));
		} else {
			// all cases covered, in doubt set to error
			releaseHotspotWithError(ctx.getString(
					R.string.start_callback_failed_unknown, reason));
		}
	}

	private void requestGroupInfo(int attempt) {
		if (LOG.isLoggable(INFO))
			LOG.info("requestGroupInfo attempt: " + attempt);

		WifiP2pManager.GroupInfoListener groupListener = group -> {
			boolean valid = isGroupValid(group);
			// If the group is valid, set the hotspot to started. If we don't
			// have any attempts left and we have anything more or less usable,
			// we try what we got
			if (valid) {
				// group is valid
				onHotspotStarted(group);
			} else if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
				// group invalid and we have attempts left
				retryRequestingGroupInfo(attempt);
			} else if (group != null) {
				// no attempts left, try what we got
				onHotspotStarted(group);
			} else {
				// no attempts left, group is null
				releaseHotspotWithError(
						ctx.getString(R.string.start_no_attempts_left));
			}
		};

		try {
			if (channel == null) return;
			wifiP2pManager.requestGroupInfo(channel, groupListener);
		} catch (SecurityException e) {
			throw new AssertionError(e);
		}
	}

	private void onHotspotStarted(WifiP2pGroup group) {
		double frequency = UNKNOWN_FREQUENCY;
		if (SDK_INT >= 29) {
			frequency = ((double) group.getFrequency()) / 1000;
		}
		listener.onHotspotStarted(new NetworkConfig(
				group.getNetworkName(), group.getPassphrase(),
				frequency));
		requestGroupInfoForConnection();
	}

	private void requestGroupInfoForConnection() {
		if (LOG.isLoggable(INFO))
			LOG.info("requestGroupInfo for connection");
		WifiP2pManager.GroupInfoListener groupListener = group -> {
			if (group == null || group.getClientList().isEmpty()) {
				handler.postDelayed(this::requestGroupInfoForConnection,
						RETRY_DELAY_MILLIS);
			} else {
				if (LOG.isLoggable(INFO)) {
					LOG.info("client list " + group.getClientList());
				}
				listener.onDeviceConnected();
			}
		};
		try {
			if (channel == null) return;
			wifiP2pManager.requestGroupInfo(channel, groupListener);
		} catch (SecurityException e) {
			throw new AssertionError(e);
		}
	}

	private boolean isGroupValid(@Nullable WifiP2pGroup group) {
		if (group == null) {
			LOG.info("group is null");
			return false;
		} else if (!group.getNetworkName().startsWith("DIRECT-")) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("received networkName without prefix 'DIRECT-': " +
						group.getNetworkName());
			}
			return false;
		} else if (networkName != null &&
				!networkName.equals(group.getNetworkName())) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("expected networkName: " + networkName);
				LOG.info("received networkName: " + group.getNetworkName());
			}
			return false;
		}
		return true;
	}

	private void retryRequestingGroupInfo(int attempt) {
		LOG.info("retrying");
		// On some devices we need to wait for the group info to become available
		if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
			handler.postDelayed(() -> requestGroupInfo(attempt + 1),
					RETRY_DELAY_MILLIS);
		} else {
			releaseHotspotWithError(
					ctx.getString(R.string.start_callback_no_group_info));
		}
	}

}
