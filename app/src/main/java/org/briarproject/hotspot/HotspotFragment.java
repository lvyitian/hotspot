package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.hotspot.HotspotState.HotspotError;
import org.briarproject.hotspot.HotspotState.HotspotStarted;
import org.briarproject.hotspot.HotspotState.HotspotStopped;
import org.briarproject.hotspot.HotspotState.NetworkConfig;
import org.briarproject.hotspot.HotspotState.StartingHotspot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.hotspot.HotspotManager.UNKNOWN_FREQUENCY;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;
import static org.briarproject.hotspot.QrCodeUtils.createWifiLoginString;

public class HotspotFragment extends Fragment {

	private MainViewModel viewModel;
	private ImageView qrCode;
	private TextView ssidView, passwordView, statusView;
	private Button button, serverButton;
	/*
	 * We keep track of whether a start has been requested by tapping the button.
	 * The PermissionUpdateCallback we pass to the ConditionManager can receive
	 * an update even if we actually did not try to start the hotspot yet. If
	 * we did not check if the user actually requested to start the hotspot, we
	 * would start the hotspot as soon as all conditions are fulfilled. We don't
	 * want that behavior, instead we want the user to actively initiate that
	 * process.
	 *
	 * We set this variable to true when the button gets tapped and reset to
	 * false as soon as we receive any status update from the view model about
	 * the hotspot status.
	 */
	private boolean startRequested = false;
	private boolean hotspotStarted = false;

	private final ConditionManager conditionManager = SDK_INT < 29 ?
			new ConditionManagerImpl(this, this::startWifiP2pHotspot) :
			new ConditionManager29Impl(this, this::startWifiP2pHotspot);

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		viewModel = new ViewModelProvider(requireActivity())
				.get(MainViewModel.class);
		conditionManager.init(requireActivity());
		return inflater.inflate(R.layout.fragment_hotspot, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View v,
			@Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);

		qrCode = v.findViewById(R.id.qr_code);
		ssidView = v.findViewById(R.id.ssid);
		passwordView = v.findViewById(R.id.password);
		statusView = v.findViewById(R.id.status);
		button = v.findViewById(R.id.button);
		button.setOnClickListener(this::onButtonClick);
		serverButton = v.findViewById(R.id.serverButton);
		serverButton.setOnClickListener(this::onServerButtonClick);

		viewModel.getIs5GhzSupported().observe(getViewLifecycleOwner(),
				b -> statusView
						.setText(getString(R.string.wifi_5ghz_supported)));

		viewModel.getStatus().observe(getViewLifecycleOwner(), state -> {
			startRequested = false;
			if (state instanceof StartingHotspot) {
				statusView.setText(getString(R.string.starting_hotspot));
			} else if (state instanceof HotspotStarted) {
				onHotspotStarted((HotspotStarted) state);
			} else if (state instanceof HotspotStopped) {
				onHotspotStopped();
			} else if (state instanceof HotspotError) {
				onHotspotError((HotspotError) state);
			}
		});
	}

	private void onHotspotStarted(HotspotStarted state) {
		button.setText(R.string.stop_hotspot);
		button.setEnabled(true);
		hotspotStarted = true;

		NetworkConfig config = state.getConfig();

		if (config.frequency == UNKNOWN_FREQUENCY)
			statusView.setText(getString(R.string.start_callback_started));
		else
			statusView.setText(getString(R.string.start_callback_started_freq,
					config.frequency));

		String qrCodeText = createWifiLoginString(config.ssid,
				config.password);
		// TODO: heavy operation should be handed off to worker thread,
		//  potentially within the view model and provide it together
		//  with NetworkConfig?
		Bitmap qrCodeBitmap = createQrCode(
				getResources().getDisplayMetrics(), qrCodeText);
		if (qrCodeBitmap == null) {
			qrCode.setVisibility(GONE);
		} else {
			qrCode.setImageBitmap(qrCodeBitmap);
			qrCode.setVisibility(VISIBLE);
		}
		ssidView.setText(getString(R.string.ssid, config.ssid));
		passwordView.setText(getString(R.string.password, config.password));

		serverButton.setVisibility(VISIBLE);
	}

	private void onHotspotStopped() {
		qrCode.setVisibility(GONE);
		ssidView.setText("");
		passwordView.setText("");

		button.setText(R.string.start_hotspot);
		button.setEnabled(true);
		hotspotStarted = false;

		statusView.setText(getString(R.string.hotspot_stopped));

		serverButton.setVisibility(GONE);
	}

	private void onHotspotError(HotspotError state) {
		onHotspotStopped();
		statusView.setText(state.getError());
	}

	@Override
	public void onStart() {
		super.onStart();
		conditionManager.onStart();
	}

	@Override
	public void onStop() {
		super.onStop();
		conditionManager.onStop();
	}

	public void onButtonClick(View view) {
		if (hotspotStarted) {
			// the hotspot is currently started → stop it
			button.setEnabled(false);
			viewModel.stopWifiP2pHotspot();
		} else {
			// the hotspot is currently stopped → start it
			button.setEnabled(false);
			startRequested = true;
			startWifiP2pHotspot();
		}
	}

	private void startWifiP2pHotspot() {
		if (startRequested && conditionManager.checkAndRequestConditions())
			viewModel.startWifiP2pHotspot();
	}

	public void onServerButtonClick(View view) {
		getParentFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, new ServerFragment())
				.addToBackStack(null)
				.commit();
	}

}
