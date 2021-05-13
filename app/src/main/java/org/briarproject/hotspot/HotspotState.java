package org.briarproject.hotspot;

abstract class HotspotState {

	static class StartingHotspot extends HotspotState {

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
		private final String url;

		HotspotStarted(NetworkConfig config, String url) {
			this.config = config;
			this.url = url;
		}

		NetworkConfig getConfig() {
			return config;
		}

		String getUrl() {
			return url;
		}
	}

	static class HotspotStopped extends HotspotState {

	}

	static class HotspotError extends HotspotState {

		private final String error;

		HotspotError(String error) {
			this.error = error;
		}

		String getError() {
			return error;
		}

	}

}
