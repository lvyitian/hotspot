package org.briarproject.hotspot;

import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import androidx.annotation.Nullable;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static com.google.zxing.BarcodeFormat.QR_CODE;

class QrCodeUtils {

	private static String TAG = QrCodeUtils.class.getName();

	static String createWifiLoginString(String ssid, String password,
										String type, boolean hidden) {
		// https://en.wikipedia.org/wiki/QR_code#WiFi_network_login
		return "WIFI:S:" + ssid + ";T:" + type + ";P:" + password + ";H:" + hidden;
	}

	@Nullable
	static Bitmap createQrCode(DisplayMetrics dm, String input) {
		int smallestDimen = Math.min(dm.widthPixels, dm.heightPixels);
		try {
			BitMatrix encoded = new QRCodeWriter().encode(input, QR_CODE,
					smallestDimen, smallestDimen);
			return renderQrCode(encoded);
		} catch (WriterException e) {
			Log.w(TAG, e);
			return null;
		}
	}

	private static Bitmap renderQrCode(BitMatrix matrix) {
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		int[] pixels = new int[width * height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				pixels[y * width + x] = matrix.get(x, y) ? BLACK : WHITE;
			}
		}
		Bitmap qr = Bitmap.createBitmap(width, height, ARGB_8888);
		qr.setPixels(pixels, 0, width, 0, 0, width, height);
		return qr;
	}
}
