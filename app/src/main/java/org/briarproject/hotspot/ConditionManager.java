package org.briarproject.hotspot;

import android.net.wifi.WifiManager;

import java.util.logging.Logger;

import androidx.fragment.app.FragmentActivity;

import static android.content.Context.WIFI_SERVICE;
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
	 * Call this during onStart() in the fragment where the ConditionManager
	 * is used.
	 */
	abstract void onStart();

	/**
	 * Check if all required conditions are met such that the hotspot can be
	 * started. If any precondition is not met yet, bring up relevant dialogs
	 * asking the user to grant relevant permissions or take relevant actions.
	 *
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	abstract boolean checkAndRequestConditions();

}
