package org.briarproject.hotspot;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.hotspot.MainViewModel.WebServerState;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.hotspot.HotspotManager.UNKNOWN_FREQUENCY;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;
import static org.briarproject.hotspot.QrCodeUtils.createWifiLoginString;

public class HotspotFragment extends Fragment {

	private MainViewModel viewModel;
	private ConditionManager conditionManager;
	private ImageView qrCode;
	private TextView ssidView, passwordView, statusView;
	private Button button, serverButton;
	private boolean hotspotStarted = false;

	private final ActivityResultLauncher<String> locationRequest =
			registerForActivityResult(new RequestPermission(), granted -> {
				conditionManager.onRequestPermissionResult(granted);
				startWifiP2pHotspot();
			});
	private final ActivityResultLauncher<Intent> wifiRequest =
			registerForActivityResult(new StartActivityForResult(), result -> {
				conditionManager.onRequestWifiEnabledResult();
				startWifiP2pHotspot();
			});

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		viewModel = new ViewModelProvider(requireActivity())
				.get(MainViewModel.class);
		conditionManager = new ConditionManager(requireActivity(),
				locationRequest, wifiRequest);
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

		viewModel.getHotSpotManager().getWifiConfiguration()
				.observe(getViewLifecycleOwner(), config -> {
					if (config == null) {
						qrCode.setVisibility(GONE);
						ssidView.setText("");
						passwordView.setText("");
						button.setText(R.string.start_hotspot);
						button.setEnabled(true);
						hotspotStarted = false;
					} else {
						String qrCodeText = createWifiLoginString(config.ssid,
								config.password, config.hidden);
						Bitmap qrCodeBitmap =
								createQrCode(getResources().getDisplayMetrics(),
										qrCodeText);
						if (qrCodeBitmap == null) {
							qrCode.setVisibility(GONE);
						} else {
							qrCode.setImageBitmap(qrCodeBitmap);
							qrCode.setVisibility(VISIBLE);
						}
						ssidView.setText(getString(R.string.ssid, config.ssid));
						passwordView.setText(
								getString(R.string.password, config.password));
						button.setText(R.string.stop_hotspot);
						button.setEnabled(true);
						hotspotStarted = true;
					}
				});

		viewModel.getIs5GhzSupported().observe(getViewLifecycleOwner(),
				b -> statusView
						.setText(getString(R.string.wifi_5ghz_supported)));

		viewModel.getHotSpotManager().getError()
				.observe(getViewLifecycleOwner(), error -> {
					if (error == null) {
						statusView.setText(getString(R.string.hotspot_stopped));
						return;
					}
					switch (error) {
						case NO_WIFI_DIRECT:
							statusView.setText(
									getString(R.string.no_wifi_direct));
							break;
						case P2P_ERROR:
							statusView.setText(
									getString(R.string.callback_failed,
											"p2p error"));
							break;
						case P2P_P2P_UNSUPPORTED:
							statusView.setText(
									getString(R.string.callback_failed,
											"p2p unsupported"));
							break;
						case P2P_NO_SERVICE_REQUESTS:
							statusView.setText(
									getString(R.string.callback_failed,
											"no service requests"));
							break;
						case PERMISSION_DENIED:
							statusView.setText(getString(
									R.string.callback_permission_denied));
							break;
						case NO_GROUP_INFO:
							statusView.setText(
									getString(R.string.callback_no_group_info));
							break;
					}
				});

		viewModel.getHotSpotManager().getStatus()
				.observe(getViewLifecycleOwner(), state -> {
					switch (state) {
						case STARTING_HOTSPOT:
							statusView.setText(
									getString(R.string.starting_hotspot));
							break;
						case HOTSPOT_STARTED:
							LiveData<Double> frequency = viewModel
									.getHotSpotManager().getFrequency();
							if (frequency.getValue() != null) {
								double freq = frequency.getValue();
								if (freq == UNKNOWN_FREQUENCY)
									statusView.setText(getString(
											R.string.callback_started));
								else statusView.setText(getString(
										R.string.callback_started_freq, freq));
							}
							break;
						case WAITING_TO_START_HOTSPOT:
							statusView.setText(
									getString(R.string.callback_waiting));
							break;
						case HOTSPOT_STOPPED:
							statusView.setText(
									getString(R.string.hotspot_stopped));
							break;
					}
				});

		viewModel.getWebServerState()
				.observe(getViewLifecycleOwner(), state -> {
					if (state == WebServerState.STOPPED) {
						serverButton.setVisibility(GONE);
					} else if (state == WebServerState.STARTED) {
						serverButton.setVisibility(VISIBLE);
					} else if (state == WebServerState.ERROR) {
						statusView.setText(R.string.web_server_error);
					}
				});
	}

	@Override
	public void onStart() {
		super.onStart();
		conditionManager.resetPermissions();
	}

	public void onButtonClick(View view) {
		if (hotspotStarted) {
			button.setEnabled(false);
			viewModel.getHotSpotManager().stopWifiP2pHotspot();
		} else {
			conditionManager.startConditionChecks();
		}
	}

	private void startWifiP2pHotspot() {
		if (conditionManager.checkAndRequestConditions()) {
			button.setEnabled(false);
			viewModel.startWifiP2pHotspot();
		}
	}

	public void onServerButtonClick(View view) {
		getParentFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, new ServerFragment())
				.addToBackStack(null)
				.commit();
	}

}
