package org.briarproject.hotspot;

import androidx.fragment.app.FragmentActivity;

/**
 * Interface for the ConditionManagers that ensure that the conditions to open a
 * hotspot are fulfilled. There are different implementations for API levels
 * lower than 29 and 29+.
 */
interface ConditionManager {

	enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	interface PermissionUpdateCallback {
		void update();
	}

	/**
	 * Pass a FragmentActivity context here during `onCreateView()`.
	 */
	void init(FragmentActivity ctx);

	/**
	 * Call this to reset state. Do this every time a user interaction triggers
	 * a request to start the hotspot to make sure we don't use internal state
	 * about permissions that we might have lost in the meantime.
	 */
	void resetPermissions();

	/**
	 * Check if all required conditions are met such that the hotspot can be
	 * started. If any precondition is not met yet, bring up relevant dialogs
	 * asking the user to grant relevant permissions or take relevant actions.
	 *
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	boolean checkAndRequestConditions();

}
