package org.briarproject.hotspot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;

import java.util.logging.Logger;

import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_DISABLED;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

/**
 * Abstract base class for the ConditionManagers that ensure that the conditions
 * to open a hotspot are fulfilled. There are different extensions of this for
 * API levels lower than 29 and 29+.
 */
abstract class ConditionManager {

	enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private static final Logger LOG =
			getLogger(ConditionManager.class.getName());

	protected final Runnable permissionUpdateCallback;
	protected FragmentActivity ctx;
	protected WifiManager wifiManager;
	protected boolean wifiP2pEnabled = false;

	ConditionManager(Runnable permissionUpdateCallback) {
		this.permissionUpdateCallback = permissionUpdateCallback;
	}

	/**
	 * Pass a FragmentActivity context here during `onCreateView()`.
	 */
	void init(FragmentActivity ctx) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
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

	/**
	 * Call this during onStart() in the fragment where the ConditionManager
	 * is used.
	 */
	void onStart() {
		ctx.registerReceiver(receiver, new IntentFilter(
				WIFI_P2P_STATE_CHANGED_ACTION));
	}

	/**
	 * Call this during onStop() in the fragment where the ConditionManager
	 * is used.
	 */
	void onStop() {
		ctx.unregisterReceiver(receiver);
	}

	/**
	 * Check if all required conditions are met such that the hotspot can be
	 * started. If any precondition is not met yet, bring up relevant dialogs
	 * asking the user to grant relevant permissions or take relevant actions.
	 *
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	abstract boolean checkAndRequestConditions();

}
