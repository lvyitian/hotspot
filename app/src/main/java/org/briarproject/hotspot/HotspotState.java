package org.briarproject.hotspot;

import androidx.annotation.Nullable;

public abstract class HotspotState {

	static class StartingHotspot extends HotspotState {

	}

	static class WaitingToStartHotspot extends HotspotState {

	}

	static class NetworkConfig {

		final String ssid, password;
		final double frequency;

		NetworkConfig(String ssid, String password, double frequency) {
			this.ssid = ssid;
			this.password = password;
			this.frequency = frequency;
		}

	}

	static class HotspotStarted extends HotspotState {

		private final NetworkConfig config;

		HotspotStarted(NetworkConfig config) {
			this.config = config;
		}

		NetworkConfig getConfig() {
			return config;
		}

	}

	static class HotspotStopped extends HotspotState {

		@Nullable
		private final String error;

		HotspotStopped(@Nullable String error) {
			this.error = error;
		}

		boolean hasError() {
			return error != null;
		}

		@Nullable
		String getError() {
			return error;
		}

	}

	static class WebServerStarted extends HotspotState {

	}

	static class WebServerStopped extends HotspotState {

	}

	static class WebServerError extends HotspotState {

	}

}
