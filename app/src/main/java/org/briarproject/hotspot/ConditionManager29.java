package org.briarproject.hotspot;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Context.WIFI_SERVICE;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static org.briarproject.hotspot.UiUtils.getGoToSettingsListener;
import static org.briarproject.hotspot.UiUtils.showDenialDialog;
import static org.briarproject.hotspot.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels >= 29.
 * <p>
 * Be sure to call {@link #onRequestPermissionResult(Boolean)} and
 * {@link #onRequestWifiEnabledResult()} when you get the
 * {@link ActivityResult}.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
@RequiresApi(29)
public class ConditionManager29 implements ConditionManager {

	private Permission wifiSetting = Permission.SHOW_RATIONALE;
	private Permission locationPermission = Permission.UNKNOWN;

	private FragmentActivity ctx;
	private WifiManager wifiManager;
	private final ActivityResultLauncher<String> locationRequest;
	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManager29(ActivityResultCaller arc,
			PermissionUpdateCallback callback) {
		locationRequest = arc.registerForActivityResult(
				new RequestPermission(), granted -> {
					onRequestPermissionResult(granted);
					callback.update();
				});

		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(), result -> {
					onRequestWifiEnabledResult();
					callback.update();
				});
	}

	@Override
	public void init(FragmentActivity ctx) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
	}

	@Override
	public void resetPermissions() {
		wifiSetting = Permission.SHOW_RATIONALE;
		locationPermission = Permission.UNKNOWN;
	}

	private boolean areEssentialPermissionsGranted() {
		return locationPermission == Permission.GRANTED
				&& wifiManager.isWifiEnabled();
	}

	@Override
	public boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (locationPermission == Permission.UNKNOWN) {
			locationRequest.launch(ACCESS_FINE_LOCATION);
			return false;
		}

		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_denied_body,
					getGoToSettingsListener(ctx));
			return false;
		}
		if (wifiSetting == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_denied_body,
					(d, w) -> requestEnableWiFi());
			return false;
		}

		// Should we show the rationale for location permission or Wi-Fi?
		if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_request_body,
					this::requestPermissions);
		} else if (wifiSetting == Permission.SHOW_RATIONALE) {
			showRationale(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi);
		}
		return false;
	}

	void onRequestPermissionResult(Boolean granted) {
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
		wifiRequest.launch(new Intent(Settings.Panel.ACTION_WIFI));
	}

	private void onRequestWifiEnabledResult() {
		wifiSetting = wifiManager.isWifiEnabled() ? Permission.GRANTED :
				Permission.PERMANENTLY_DENIED;
	}

}
