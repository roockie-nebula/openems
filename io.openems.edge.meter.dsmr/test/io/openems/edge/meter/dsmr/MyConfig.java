package io.openems.edge.meter.dsmr;

import io.openems.common.types.MeterType;
import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id;
		private boolean enabled = true;
		private MeterType type = MeterType.GRID;
		private String port = "/dev/ttyUSB0";
		private DsmrVersion dsmrVersion = DsmrVersion.V5_0;
		private boolean invert = false;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder setType(MeterType type) {
			this.type = type;
			return this;
		}

		public Builder setPort(String port) {
			this.port = port;
			return this;
		}

		public Builder setDsmrVersion(DsmrVersion v) {
			this.dsmrVersion = v;
			return this;
		}

		public Builder setInvert(boolean invert) {
			this.invert = invert;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String alias() {
		return "";
	}

	@Override
	public boolean enabled() {
		return this.builder.enabled;
	}

	@Override
	public MeterType type() {
		return this.builder.type;
	}

	@Override
	public String port() {
		return this.builder.port;
	}

	@Override
	public DsmrVersion dsmrVersion() {
		return this.builder.dsmrVersion;
	}

	@Override
	public boolean invert() {
		return this.builder.invert;
	}
}
