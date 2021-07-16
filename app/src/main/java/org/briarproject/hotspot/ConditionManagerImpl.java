package org.briarproject.hotspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import java.util.logging.Logger;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_DISABLED;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels < 29.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
public class ConditionManagerImpl implements ConditionManager {

	private static final Logger LOG =
			getLogger(ConditionManagerImpl.class.getName());

	private FragmentActivity ctx;
	private WifiManager wifiManager;
	private final ActivityResultLauncher<Intent> wifiRequest;
	private boolean wifiP2pEnabled = false;
	private final Runnable permissionUpdateCallback;

	ConditionManagerImpl(ActivityResultCaller arc,
			Runnable permissionUpdateCallback) {
		this.permissionUpdateCallback = permissionUpdateCallback;
		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(),
				result -> permissionUpdateCallback.run());
	}

	final BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		@UiThread
		public void onReceive(Context context, Intent intent) {
			if (!WIFI_P2P_STATE_CHANGED_ACTION.equals(intent.getAction())) {
				return;
			}
			int state = intent.getIntExtra(EXTRA_WIFI_STATE,
					WIFI_P2P_STATE_DISABLED);
			wifiP2pEnabled = state == WIFI_P2P_STATE_ENABLED;
			if (LOG.isLoggable(INFO))
				LOG.info("WifiP2pState: " + wifiP2pEnabled);
			permissionUpdateCallback.run();
		}

	};

	@Override
	public void init(FragmentActivity ctx) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
	}

	@Override
	public void onStart() {
		ctx.registerReceiver(receiver, new IntentFilter(
				WIFI_P2P_STATE_CHANGED_ACTION));
	}

	@Override
	public void onStop() {
		ctx.unregisterReceiver(receiver);
	}

	private boolean areEssentialPermissionsGranted() {
		if (LOG.isLoggable(INFO)) {
			LOG.info(String.format("areEssentialPermissionsGranted():" +
							"wifiManager.isWifiEnabled()? %b," +
							"wifiP2pEnabled? %b",
					wifiManager.isWifiEnabled(),
					wifiP2pEnabled));
		}
		return wifiManager.isWifiEnabled() && wifiP2pEnabled;
	}

	@Override
	public boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (!wifiManager.isWifiEnabled()) {
			// Try enabling the Wifi and return true if that seems to have been
			// successful, i.e. "Wifi is either already in the requested state, or
			// in progress toward the requested state".
			if (wifiManager.setWifiEnabled(true)) {
				LOG.info("Enabled wifi");
				return false;
			}

			// Wifi is not enabled and we can't seem to enable it, so ask the user
			// to enable it for us.
			showRationale(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi);
		}

		return wifiP2pEnabled;
	}

	private void requestEnableWiFi() {
		wifiRequest.launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
	}

}
