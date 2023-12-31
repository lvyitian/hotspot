package org.briarproject.hotspot;

import android.content.Intent;
import android.provider.Settings;

import java.util.logging.Logger;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.hotspot.UiUtils.getGoToSettingsListener;
import static org.briarproject.hotspot.UiUtils.showDenialDialog;
import static org.briarproject.hotspot.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels >= 29.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
@RequiresApi(29)
class ConditionManager29Impl extends ConditionManager {

	private static final Logger LOG =
			getLogger(ConditionManager29Impl.class.getName());

	private Permission locationPermission = Permission.UNKNOWN;

	private final ActivityResultLauncher<String> locationRequest;
	private final ActivityResultLauncher<Intent> wifiRequest;
	/**
	 * We keep track here whether a wifi request is currently running. It is used
	 * for two things:
	 * <p>
	 * 1. If a Wifi request is still running, then areEssentialPermissionsGranted()
	 * will still return false, so that we don't jump to the next screen while
	 * the fragment is still overlayed with the snack bar style settings action.
	 * <p>
	 * 2. If a Wifi request is running, do not attempt to start another one
	 * during checkAndRequestConditions(). Otherwise we would show the rational
	 * dialog prompting the user to enable Wifi after the the user enabled Wifi
	 * and the WifiP2p broadcast got received even though the user has not
	 * dismissed the settings action yet (which is the expected situation,
	 * because one needs to dismiss that action quickly in order to be quicker
	 * than WifiP2p being enabled.
	 * <p>
	 * This flag is set to true during requestEnableWiFi() and set to false in
	 * the activity result callback.
	 */
	private boolean wifiRequestInProgress = false;

	ConditionManager29Impl(ActivityResultCaller arc,
			Runnable permissionUpdateCallback) {
		super(permissionUpdateCallback);
		locationRequest = arc.registerForActivityResult(
				new RequestPermission(), granted -> {
					onRequestPermissionResult(granted);
					permissionUpdateCallback.run();
				});
		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(),
				result -> {
					wifiRequestInProgress = false;
					permissionUpdateCallback.run();
				});
	}

	@Override
	void onStart() {
		super.onStart();
		locationPermission = Permission.UNKNOWN;
	}

	private boolean areEssentialPermissionsGranted() {
		if (LOG.isLoggable(INFO)) {
			LOG.info(String.format("areEssentialPermissionsGranted():" +
							"locationPermission? %s, " +
							"wifiRequestInProgress? %b, " +
							"wifiManager.isWifiEnabled()? %b, " +
							"wifiP2pEnabled? %b",
					locationPermission,
					wifiRequestInProgress,
					wifiManager.isWifiEnabled(),
					wifiP2pEnabled));
		}
		return locationPermission == Permission.GRANTED &&
				!wifiRequestInProgress && wifiManager.isWifiEnabled() &&
				wifiP2pEnabled;
	}

	@Override
	boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (locationPermission == Permission.UNKNOWN) {
			locationRequest.launch(ACCESS_FINE_LOCATION);
			return false;
		}

		// If the location permission has been permanently denied, ask the
		// user to change the setting
		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_denied_body,
					getGoToSettingsListener(ctx));
			return false;
		}

		// Should we show the rationale for location permission?
		if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_request_body,
					this::requestPermissions);
			return false;
		}

		// If Wifi is not enabled, we show the rationale for enabling Wifi?
		if (!wifiRequestInProgress && !wifiManager.isWifiEnabled()) {
			showRationale(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi);
		}
		return false;
	}

	private void onRequestPermissionResult(@Nullable Boolean granted) {
		if (granted != null && granted) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRequestPermissionRationale(ctx,
				ACCESS_FINE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
		}
	}

	private void requestPermissions() {
		locationRequest.launch(ACCESS_FINE_LOCATION);
	}

	private void requestEnableWiFi() {
		wifiRequestInProgress = true;
		wifiRequest.launch(new Intent(Settings.Panel.ACTION_WIFI));
	}

}
