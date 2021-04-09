package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.briarproject.hotspot.MainViewModel.WebServerState;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;
import static org.briarproject.hotspot.QrCodeUtils.createWifiLoginString;

public class HotspotFragment extends Fragment {

    private MainViewModel viewModel;
    private ImageView qrCode;
    private TextView ssidView, passwordView, statusView;
    private Button button, serverButton;
    private boolean hotspotStarted = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_hotspot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        qrCode = v.findViewById(R.id.qr_code);
        ssidView = v.findViewById(R.id.ssid);
        passwordView = v.findViewById(R.id.password);
        statusView = v.findViewById(R.id.status);
        button = v.findViewById(R.id.button);
        button.setOnClickListener(this::onButtonClick);
        serverButton = v.findViewById(R.id.serverButton);
        serverButton.setOnClickListener(this::onServerButtonClick);

        viewModel.getWifiConfiguration().observe(getViewLifecycleOwner(), config -> {
            if (config == null) {
                qrCode.setVisibility(GONE);
                ssidView.setText("");
                passwordView.setText("");
                button.setText(R.string.start_hotspot);
                button.setEnabled(true);
                hotspotStarted = false;
            } else {
                String qrCodeText = createWifiLoginString(config.ssid, config.password,
                        config.hidden);
                Bitmap qrCodeBitmap = createQrCode(getResources().getDisplayMetrics(), qrCodeText);
                if (qrCodeBitmap == null) {
                    qrCode.setVisibility(GONE);
                } else {
                    qrCode.setImageBitmap(qrCodeBitmap);
                    qrCode.setVisibility(VISIBLE);
                }
                ssidView.setText(getString(R.string.ssid, config.ssid));
                passwordView.setText(getString(R.string.password, config.password));
                button.setText(R.string.stop_hotspot);
                button.setEnabled(true);
                hotspotStarted = true;
            }
        });

        viewModel.getStatus().observe(getViewLifecycleOwner(), status -> statusView.setText(status));
        if (SDK_INT >= 29 && (checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) != PERMISSION_GRANTED)) {
            requestPermissions(new String[]{ACCESS_FINE_LOCATION}, 0);
        }

        viewModel.getWebServerState().observe(getViewLifecycleOwner(), state -> {
            if (state == WebServerState.STOPPED) {
                serverButton.setVisibility(GONE);
            } else if (state == WebServerState.STARTED) {
                serverButton.setVisibility(VISIBLE);
            } else if (state == WebServerState.ERROR) {
                statusView.setText(R.string.web_server_error);
            }
        });
    }

    public void onButtonClick(View view) {
        button.setEnabled(false);
        if (hotspotStarted) viewModel.stopWifiP2pHotspot();
        else viewModel.startWifiP2pHotspot();
    }

    public void onServerButtonClick(View view) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ServerFragment())
                .addToBackStack(null)
                .commit();
    }

}
