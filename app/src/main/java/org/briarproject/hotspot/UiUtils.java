package org.briarproject.hotspot;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import static android.content.Intent.CATEGORY_DEFAULT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static org.briarproject.hotspot.BuildConfig.APPLICATION_ID;

class UiUtils {

	static DialogInterface.OnClickListener getGoToSettingsListener(
			Context context) {
		return (dialog, which) -> {
			Intent i = new Intent();
			i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
			i.addCategory(CATEGORY_DEFAULT);
			i.setData(Uri.parse("package:" + APPLICATION_ID));
			i.addFlags(FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);
		};
	}

	static void showDenialDialog(FragmentActivity ctx, @StringRes int title,
			@StringRes int body, DialogInterface.OnClickListener onOkClicked,
			Runnable onDismiss) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setPositiveButton(R.string.ok, onOkClicked);
		builder.setNegativeButton(R.string.cancel,
				(dialog, which) -> ctx.supportFinishAfterTransition());
		builder.setOnDismissListener(dialog -> onDismiss.run());
		builder.show();
	}

	static void showRationale(Context ctx, @StringRes int title,
			@StringRes int body,
			Runnable onContinueClicked, Runnable onDismiss) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> onContinueClicked.run());
		builder.setOnDismissListener(dialog -> onDismiss.run());
		builder.show();
	}

}
