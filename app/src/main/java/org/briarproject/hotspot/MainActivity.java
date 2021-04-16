package org.briarproject.hotspot;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;

public class MainActivity extends AppCompatActivity {

	MainViewModel viewModel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		viewModel = new ViewModelProvider(this).get(MainViewModel.class);

		if (SDK_INT >= 29) requestPermissions();
	}

	@RequiresApi(29)
	private void requestPermissions() {
		if (checkSelfPermission(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
			requestPermissions(new String[]{ACCESS_FINE_LOCATION}, 0);
		}
		if (viewModel.needToAskForEnablingWifi()) {
			Intent i = new Intent(Settings.Panel.ACTION_WIFI);
			startActivity(i);
		}
	}
}
