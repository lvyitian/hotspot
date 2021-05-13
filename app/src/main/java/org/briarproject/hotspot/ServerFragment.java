package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.hotspot.QrCodeUtils.createQrCode;

public class ServerFragment extends Fragment {

	private MainViewModel viewModel;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		viewModel = new ViewModelProvider(requireActivity())
				.get(MainViewModel.class);
		// Inflate the layout for this fragment
		return inflater.inflate(R.layout.fragment_server, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View v,
			@Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);
		ImageView qrCode = v.findViewById(R.id.qr_code);
		TextView urlView = v.findViewById(R.id.url);

		viewModel.getStatus().observe(getViewLifecycleOwner(), status -> {
			if (status instanceof HotspotState.HotspotStarted) {
				HotspotState.HotspotStarted state =
						(HotspotState.HotspotStarted) status;
				Bitmap qrCodeBitmap = createQrCode(
						getResources().getDisplayMetrics(), state.getUrl());
				if (qrCodeBitmap == null) {
					qrCode.setVisibility(GONE);
				} else {
					qrCode.setImageBitmap(qrCodeBitmap);
					qrCode.setVisibility(VISIBLE);
				}
				urlView.setText(state.getUrl());
			}
		});
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu,
			@NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.interfaces) {
			getParentFragmentManager().beginTransaction()
					.replace(R.id.fragment_container, new InterfacesFragment())
					.addToBackStack("INTERFACES")
					.commit();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
