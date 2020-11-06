package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;
import static org.briarproject.hotspot.QrCodeUtils.createWifiLoginString;

public class MainActivity extends AppCompatActivity {

	private MainViewModel viewModel;
	private ImageView qrCode;
	private TextView ssidView, passwordView, statusView;
	private Button button;
	private boolean hotspotStarted = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
		viewModel.setApplication(getApplication());

		qrCode = findViewById(R.id.qr_code);
		ssidView = findViewById(R.id.ssid);
		passwordView = findViewById(R.id.password);
		statusView = findViewById(R.id.status);
		button = findViewById(R.id.button);

		viewModel.getWifiConfiguration().observe(this, config -> {
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

		viewModel.getStatus().observe(this, status -> statusView.setText(status));

		if (SDK_INT >= 29 && (checkSelfPermission(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED)) {
			requestPermissions(new String[]{ACCESS_FINE_LOCATION}, 0);
		}
	}

	public void onButtonClick(View view) {
		button.setEnabled(false);
		if (hotspotStarted) viewModel.stopWifiP2pHotspot();
		else viewModel.startWifiP2pHotspot();
	}
}
