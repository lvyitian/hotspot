package org.briarproject.hotspot;

import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;
import static org.briarproject.hotspot.UiUtils.showDenialDialog;
import static org.briarproject.hotspot.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels < 29.
 * <p>
 * Be sure to call and {@link #onRequestWifiEnabledResult()} when you get the
 * {@link ActivityResult}.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
public class ConditionManager28 implements ConditionManager {

	private Permission wifiSetting = Permission.SHOW_RATIONALE;

	private FragmentActivity ctx;
	private WifiManager wifiManager;
	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManager28(ActivityResultCaller arc,
			PermissionUpdateCallback callback) {
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
	}

	private boolean areEssentialPermissionsGranted() {
		return wifiManager.isWifiEnabled();
	}

	@Override
	public boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (wifiManager.setWifiEnabled(true)) {
			return true;
		}

		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (wifiSetting == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_denied_body,
					(d, w) -> requestEnableWiFi());
			return false;
		}

		// Should we show the rationale for Wi-Fi permission?
		if (wifiSetting == Permission.SHOW_RATIONALE) {
			showRationale(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi);
		}
		return false;
	}

	private void requestEnableWiFi() {
		wifiRequest.launch(new Intent(Settings.ACTION_WIFI_SETTINGS));
	}

	private void onRequestWifiEnabledResult() {
		wifiSetting = wifiManager.isWifiEnabled() ? Permission.GRANTED :
				Permission.PERMANENTLY_DENIED;
	}

}
