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

	/**
	 * Pass a FragmentActivity context here during `onCreateView()`.
	 */
	void init(FragmentActivity ctx);

	/**
	 * Call this during onStart() in the fragment where the ConditionManager
	 * is used.
	 */
	void onStart();

	/**
	 * Call this during onStop() in the fragment where the ConditionManager
	 * is used.
	 */
	void onStop();

	/**
	 * Check if all required conditions are met such that the hotspot can be
	 * started. If any precondition is not met yet, bring up relevant dialogs
	 * asking the user to grant relevant permissions or take relevant actions.
	 *
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	boolean checkAndRequestConditions();

}
