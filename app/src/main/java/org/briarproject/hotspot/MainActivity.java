package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;
import static org.briarproject.hotspot.QrCodeUtils.createWifiLoginString;

@SuppressWarnings("deprecation")
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
				hotspotStarted = false;
			} else {
				String ssid = config.SSID;
				String password = config.preSharedKey;
				String qrCodeText = createWifiLoginString(ssid, password, "WPA", false);
				Bitmap qrCodeBitmap = createQrCode(getResources().getDisplayMetrics(), qrCodeText);
				if (qrCodeBitmap == null) {
					qrCode.setVisibility(GONE);
				} else {
					qrCode.setImageBitmap(qrCodeBitmap);
					qrCode.setVisibility(VISIBLE);
				}
				ssidView.setText(getString(R.string.ssid, config.SSID));
				passwordView.setText(getString(R.string.password, config.preSharedKey));
				button.setText(R.string.stop_hotspot);
				hotspotStarted = true;
			}
		});

		viewModel.getStatus().observe(this, status -> statusView.setText(status));
	}

	public void onButtonClick(View view) {
		if (SDK_INT >= 26) {
			if (ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
					== PERMISSION_GRANTED) {
				startOrStopHotspot();
			} else {
				requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, 0);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (SDK_INT >= 26 && grantResults.length == 1 && grantResults[0] == PERMISSION_GRANTED) {
			startOrStopHotspot();
		}
	}

	@RequiresApi(26)
	private void startOrStopHotspot() {
		if (hotspotStarted) viewModel.stopHotspot();
		else viewModel.startHotspot();
	}
}
