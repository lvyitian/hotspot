package org.briarproject.hotspot;

import android.content.Intent;
import android.provider.Settings;

import java.util.logging.Logger;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.fragment.app.FragmentActivity;

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
class ConditionManagerImpl extends ConditionManager {

	private static final Logger LOG =
			getLogger(ConditionManagerImpl.class.getName());

	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManagerImpl(ActivityResultCaller arc,
			Runnable permissionUpdateCallback) {
		super(permissionUpdateCallback);
		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(),
				result -> permissionUpdateCallback.run());
	}

	@Override
	void init(FragmentActivity ctx) {
		super.init(ctx);
	}

	private boolean areEssentialPermissionsGranted() {
		if (LOG.isLoggable(INFO)) {
			LOG.info(String.format("areEssentialPermissionsGranted():" +
							"wifiManager.isWifiEnabled()? %b, " +
							"wifiP2pEnabled? %b",
					wifiManager.isWifiEnabled(),
					wifiP2pEnabled));
		}
		return wifiManager.isWifiEnabled() && wifiP2pEnabled;
	}

	@Override
	boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (!wifiManager.isWifiEnabled()) {
			// Try enabling the Wifi and return true if that seems to have been
			// successful, i.e. "Wifi is either already in the requested state, or
			// in progress toward the requested state".
			if (wifiManager.setWifiEnabled(true)) {
				LOG.info("Enabled wifi");
				return wifiP2pEnabled;
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
