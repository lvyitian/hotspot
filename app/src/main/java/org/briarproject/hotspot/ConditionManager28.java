package org.briarproject.hotspot;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;
import static org.briarproject.hotspot.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels < 29.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
public class ConditionManager28 implements ConditionManager {

	private FragmentActivity ctx;
	private WifiManager wifiManager;
	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManager28(ActivityResultCaller arc,
			PermissionUpdateCallback callback) {
		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(), result -> callback.update());
	}

	@Override
	public void init(FragmentActivity ctx) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
	}

	@Override
	public void resetPermissions() {
	}

	private boolean areEssentialPermissionsGranted() {
		return wifiManager.isWifiEnabled();
	}

	@Override
	public boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		// Try enabling the Wifi and return true if that seems to have been
		// successful, i.e. "Wifi is either already in the requested state, or
		// in progress toward the requested state".
		if (wifiManager.setWifiEnabled(true)) {
			return true;
		}

		// Wifi is not enabled and we can't seem to enable it, so ask the user
		// to enable it for us.
		showRationale(ctx, R.string.wifi_settings_title,
				R.string.wifi_settings_request_enable_body,
				this::requestEnableWiFi);

		return false;
	}

	private void requestEnableWiFi() {
		wifiRequest.launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
	}

}
