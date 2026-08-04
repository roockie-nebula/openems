package io.openems.edge.meter.modbus;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.MeterType;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.common.taskmanager.Priority;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String alias = "";
		private String modbusId = "modbus0";
		private int modbusUnitId = 1;
		private MeterType type = MeterType.CONSUMPTION_METERED;
		private Priority priority = Priority.HIGH;
		private Fc powerFunctionCode = Fc.FC3;
		private int powerAddress = 0;
		private Encoding powerEncoding = Encoding.INT32;
		private WordOrder powerWordOrder = WordOrder.MSWLSW;
		private int powerScaleFactor = 0;
		private boolean invertPower = false;
		private EnergySource energySource = EnergySource.REGISTER;
		private Fc energyFunctionCode = Fc.FC3;
		private int energyAddress = 0;
		private Encoding energyEncoding = Encoding.INT32;
		private WordOrder energyWordOrder = WordOrder.MSWLSW;
		private int energyScaleFactor = 0;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setAlias(String alias) {
			this.alias = alias;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public Builder setModbusUnitId(int modbusUnitId) {
			this.modbusUnitId = modbusUnitId;
			return this;
		}

		public Builder setType(MeterType type) {
			this.type = type;
			return this;
		}

		public Builder setPriority(Priority priority) {
			this.priority = priority;
			return this;
		}

		public Builder setPowerFunctionCode(Fc powerFunctionCode) {
			this.powerFunctionCode = powerFunctionCode;
			return this;
		}

		public Builder setPowerAddress(int powerAddress) {
			this.powerAddress = powerAddress;
			return this;
		}

		public Builder setPowerEncoding(Encoding powerEncoding) {
			this.powerEncoding = powerEncoding;
			return this;
		}

		public Builder setPowerWordOrder(WordOrder powerWordOrder) {
			this.powerWordOrder = powerWordOrder;
			return this;
		}

		public Builder setPowerScaleFactor(int powerScaleFactor) {
			this.powerScaleFactor = powerScaleFactor;
			return this;
		}

		public Builder setInvertPower(boolean invertPower) {
			this.invertPower = invertPower;
			return this;
		}

		public Builder setEnergySource(EnergySource energySource) {
			this.energySource = energySource;
			return this;
		}

		public Builder setEnergyFunctionCode(Fc energyFunctionCode) {
			this.energyFunctionCode = energyFunctionCode;
			return this;
		}

		public Builder setEnergyAddress(int energyAddress) {
			this.energyAddress = energyAddress;
			return this;
		}

		public Builder setEnergyEncoding(Encoding energyEncoding) {
			this.energyEncoding = energyEncoding;
			return this;
		}

		public Builder setEnergyWordOrder(WordOrder energyWordOrder) {
			this.energyWordOrder = energyWordOrder;
			return this;
		}

		public Builder setEnergyScaleFactor(int energyScaleFactor) {
			this.energyScaleFactor = energyScaleFactor;
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
		return this.builder.alias;
	}

	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public String Modbus_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.modbus_id());
	}

	@Override
	public int modbusUnitId() {
		return this.builder.modbusUnitId;
	}

	@Override
	public MeterType type() {
		return this.builder.type;
	}

	@Override
	public Priority priority() {
		return this.builder.priority;
	}

	@Override
	public Fc powerFunctionCode() {
		return this.builder.powerFunctionCode;
	}

	@Override
	public int powerAddress() {
		return this.builder.powerAddress;
	}

	@Override
	public Encoding powerEncoding() {
		return this.builder.powerEncoding;
	}

	@Override
	public WordOrder powerWordOrder() {
		return this.builder.powerWordOrder;
	}

	@Override
	public int powerScaleFactor() {
		return this.builder.powerScaleFactor;
	}

	@Override
	public boolean invertPower() {
		return this.builder.invertPower;
	}

	@Override
	public EnergySource energySource() {
		return this.builder.energySource;
	}

	@Override
	public Fc energyFunctionCode() {
		return this.builder.energyFunctionCode;
	}

	@Override
	public int energyAddress() {
		return this.builder.energyAddress;
	}

	@Override
	public Encoding energyEncoding() {
		return this.builder.energyEncoding;
	}

	@Override
	public WordOrder energyWordOrder() {
		return this.builder.energyWordOrder;
	}

	@Override
	public int energyScaleFactor() {
		return this.builder.energyScaleFactor;
	}

}
