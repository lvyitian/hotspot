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
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_DISABLED;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED;
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
public class ConditionManager29Impl implements ConditionManager {

	private static final Logger LOG =
			getLogger(ConditionManager29Impl.class.getName());

	private Permission locationPermission = Permission.UNKNOWN;

	private FragmentActivity ctx;
	private WifiManager wifiManager;
	private final ActivityResultLauncher<String> locationRequest;
	private final ActivityResultLauncher<Intent> wifiRequest;
	private boolean wifiP2pEnabled = false;
	private boolean wifiRequestInProgress = false;
	private final Runnable permissionUpdateCallback;

	ConditionManager29Impl(ActivityResultCaller arc,
			Runnable permissionUpdateCallback) {
		this.permissionUpdateCallback = permissionUpdateCallback;
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

	/**
	 * When Wifi is off and gets enabled using {@link WifiManager#setWifiEnabled},
	 * it takes a while until Wifi P2P is also available and it is safe to call
	 * {@link android.net.wifi.p2p.WifiP2pManager#createGroup}. On API levels 29
	 * and above, there's {@link android.net.wifi.p2p.WifiP2pManager#requestP2pState},
	 * but on pre 29 the only thing we can do is register a broadcast on
	 * WIFI_P2P_STATE_CHANGED_ACTION and wait until Wifi P2P is available.
	 * Issue #2088 uncovered that this is necessary.
	 */
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
		locationPermission = Permission.UNKNOWN;
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
		return locationPermission == Permission.GRANTED &&
				!wifiRequestInProgress && wifiManager.isWifiEnabled() &&
				wifiP2pEnabled;
	}

	@Override
	public boolean checkAndRequestConditions() {
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

	private void onRequestPermissionResult(Boolean granted) {
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
