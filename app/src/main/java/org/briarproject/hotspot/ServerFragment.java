package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
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
    private ImageView qrCode;
    private TextView urlView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_server, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        qrCode = v.findViewById(R.id.qr_code);
        urlView = v.findViewById(R.id.url);

        String text = "http://192.168.49.1:9999";

        Bitmap qrCodeBitmap = createQrCode(getResources().getDisplayMetrics(), text);
        if (qrCodeBitmap == null) {
            qrCode.setVisibility(GONE);
        } else {
            qrCode.setImageBitmap(qrCodeBitmap);
            qrCode.setVisibility(VISIBLE);
        }
        urlView.setText(text);
    }

}
