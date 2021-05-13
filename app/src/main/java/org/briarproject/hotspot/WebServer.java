package org.briarproject.hotspot;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
import static fi.iki.elonen.NanoHTTPD.Response.Status.OK;

public class WebServer extends NanoHTTPD {

	final static int PORT = 9999;

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
			String msg = "<html><body><h1>Download Offline Hotspot App</h1>\n";
			msg += "<h2><a href=\"/app.apk\">Click here to download</a></h2>";
			msg +=
					"After download is complete, open the downloaded file and install it.";
			return newFixedLengthResponse(msg + "</body></html>\n");
		}
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
