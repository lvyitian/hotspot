package org.briarproject.hotspot;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import fi.iki.elonen.NanoHTTPD;

import static android.util.Xml.Encoding.UTF_8;
import static fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
import static fi.iki.elonen.NanoHTTPD.Response.Status.OK;
import static java.util.Objects.requireNonNull;
import static org.briarproject.hotspot.BuildConfig.VERSION_NAME;

public class WebServer extends NanoHTTPD {

	final static int PORT = 9999;
	private static final String FILE_HTML = "hotspot.html";
	private final Pattern REGEX_AGENT = Pattern.compile("Android ([0-9]+)");

	private final Context ctx;

	public WebServer(Context ctx) {
		super(PORT);
		this.ctx = ctx;
	}

	public void start() throws IOException {
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
	}

	@Override
	public Response serve(IHTTPSession session) {
		if (session.getUri().endsWith("app.apk")) {
			return serveApk();
		} else {
			Response res;
			try {
				String html = getHtml(session.getHeaders().get("user-agent"));
				res = newFixedLengthResponse(OK, MIME_HTML, html);
			} catch (Exception e) {
				e.printStackTrace();
				res = newFixedLengthResponse(INTERNAL_ERROR, MIME_PLAINTEXT,
						INTERNAL_ERROR.getDescription());
			}
			return res;
		}
	}

	private String getHtml(@Nullable String userAgent) throws Exception {
		Document doc;
		try (InputStream is = ctx.getAssets().open(FILE_HTML)) {
			doc = Jsoup.parse(is, UTF_8.name(), "");
		}
		String app = ctx.getString(R.string.app_name);
		String appV = app + " " + VERSION_NAME;
		doc.select("#download_title").first()
				.text(ctx.getString(R.string.website_download_title, appV));
		doc.select("#download_intro").first()
				.text(ctx.getString(R.string.website_download_intro, app));
		doc.select("#download_button").first()
				.text(ctx.getString(R.string.website_download_title, app));
		doc.select("#download_outro").first()
				.text(ctx.getString(R.string.website_download_outro));
		doc.select("#troubleshooting_title").first()
				.text(ctx.getString(R.string.website_troubleshooting_title));
		doc.select("#troubleshooting_1").first()
				.text(ctx.getString(R.string.website_troubleshooting_1));
		doc.select("#troubleshooting_2").first()
				.text(getUnknownSourcesString(userAgent));
		return doc.outerHtml();
	}

	private String getUnknownSourcesString(String userAgent) {
		boolean is8OrHigher = false;
		if (userAgent != null) {
			Matcher matcher = REGEX_AGENT.matcher(userAgent);
			if (matcher.find()) {
				int androidMajorVersion =
						Integer.parseInt(requireNonNull(matcher.group(1)));
				is8OrHigher = androidMajorVersion >= 8;
			}
		}
		return is8OrHigher ?
				ctx.getString(R.string.website_troubleshooting_2_new) :
				ctx.getString(R.string.website_troubleshooting_2_old);
	}

	private Response serveApk() {
		String mime = "application/vnd.android.package-archive";

		File file = new File(ctx.getPackageCodePath());
		long fileLen = file.length();

		Response res;
		try {
			FileInputStream fis = new FileInputStream(file);
			res = newFixedLengthResponse(OK, mime, fis, fileLen);
			res.addHeader("Content-Length", "" + fileLen);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			res = newFixedLengthResponse(NOT_FOUND, MIME_PLAINTEXT,
					"Error 404, file not found.");
		}
		return res;
	}
}
