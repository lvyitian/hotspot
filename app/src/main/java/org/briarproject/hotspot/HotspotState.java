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

		private NetworkConfig config;

		HotspotStarted(NetworkConfig config) {
			this.config = config;
		}

		NetworkConfig getConfig() {
			return config;
		}

	}

	enum HotspotError {
		NO_WIFI_DIRECT,
		P2P_ERROR,
		P2P_P2P_UNSUPPORTED,
		P2P_NO_SERVICE_REQUESTS,
		PERMISSION_DENIED,
		NO_GROUP_INFO,
		OTHER
	}

	static class HotspotStopped extends HotspotState {

		private HotspotError error;

		public HotspotStopped(HotspotError error) {
			this.error = error;
		}

		boolean hasError() {
			return error != null;
		}

		@Nullable
		HotspotError getError() {
			return error;
		}

	}

}
