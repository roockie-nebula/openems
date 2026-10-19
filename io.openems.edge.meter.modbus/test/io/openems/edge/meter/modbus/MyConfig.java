package io.openems.edge.meter.modbus;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.MeterType;
import io.openems.common.utils.ConfigUtils;
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
		private PowerSource powerSource = PowerSource.READ_FROM_REGISTER;
		private RegisterType powerRegisterType = RegisterType.HOLDING_REGISTERS;
		private int powerAddress = 0;
		private Encoding powerEncoding = Encoding.INT32;
		private WordOrder powerWordOrder = WordOrder.HIGH_WORD_FIRST;
		private ScaleFactor powerScaleFactor = ScaleFactor.ONE;
		private boolean powerInvert = false;
		private EnergySource energySource = EnergySource.READ_FROM_REGISTER;
		private RegisterType energyRegisterType = RegisterType.HOLDING_REGISTERS;
		private int energyAddress = 0;
		private Encoding energyEncoding = Encoding.INT32;
		private WordOrder energyWordOrder = WordOrder.HIGH_WORD_FIRST;
		private ScaleFactor energyScaleFactor = ScaleFactor.ONE;
		private boolean reverseEnergyEnabled = false;
		private RegisterType reverseEnergyRegisterType = RegisterType.HOLDING_REGISTERS;
		private int reverseEnergyAddress = 0;
		private Encoding reverseEnergyEncoding = Encoding.INT32;
		private WordOrder reverseEnergyWordOrder = WordOrder.HIGH_WORD_FIRST;
		private ScaleFactor reverseEnergyScaleFactor = ScaleFactor.ONE;

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

		public Builder setPowerSource(PowerSource powerSource) {
			this.powerSource = powerSource;
			return this;
		}

		public Builder setPowerRegisterType(RegisterType powerRegisterType) {
			this.powerRegisterType = powerRegisterType;
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

		public Builder setPowerScaleFactor(ScaleFactor powerScaleFactor) {
			this.powerScaleFactor = powerScaleFactor;
			return this;
		}

		public Builder setPowerInvert(boolean powerInvert) {
			this.powerInvert = powerInvert;
			return this;
		}

		public Builder setEnergySource(EnergySource energySource) {
			this.energySource = energySource;
			return this;
		}

		public Builder setEnergyRegisterType(RegisterType energyRegisterType) {
			this.energyRegisterType = energyRegisterType;
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

		public Builder setEnergyScaleFactor(ScaleFactor energyScaleFactor) {
			this.energyScaleFactor = energyScaleFactor;
			return this;
		}

		public Builder setReverseEnergyEnabled(boolean reverseEnergyEnabled) {
			this.reverseEnergyEnabled = reverseEnergyEnabled;
			return this;
		}

		public Builder setReverseEnergyRegisterType(RegisterType reverseEnergyRegisterType) {
			this.reverseEnergyRegisterType = reverseEnergyRegisterType;
			return this;
		}

		public Builder setReverseEnergyAddress(int reverseEnergyAddress) {
			this.reverseEnergyAddress = reverseEnergyAddress;
			return this;
		}

		public Builder setReverseEnergyEncoding(Encoding reverseEnergyEncoding) {
			this.reverseEnergyEncoding = reverseEnergyEncoding;
			return this;
		}

		public Builder setReverseEnergyWordOrder(WordOrder reverseEnergyWordOrder) {
			this.reverseEnergyWordOrder = reverseEnergyWordOrder;
			return this;
		}

		public Builder setReverseEnergyScaleFactor(ScaleFactor reverseEnergyScaleFactor) {
			this.reverseEnergyScaleFactor = reverseEnergyScaleFactor;
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
	public PowerSource powerSource() {
		return this.builder.powerSource;
	}

	@Override
	public RegisterType powerRegisterType() {
		return this.builder.powerRegisterType;
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
	public ScaleFactor powerScaleFactor() {
		return this.builder.powerScaleFactor;
	}

	@Override
	public boolean powerInvert() {
		return this.builder.powerInvert;
	}

	@Override
	public EnergySource energySource() {
		return this.builder.energySource;
	}

	@Override
	public RegisterType energyRegisterType() {
		return this.builder.energyRegisterType;
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
	public ScaleFactor energyScaleFactor() {
		return this.builder.energyScaleFactor;
	}

	@Override
	public boolean reverseEnergyEnabled() {
		return this.builder.reverseEnergyEnabled;
	}

	@Override
	public RegisterType reverseEnergyRegisterType() {
		return this.builder.reverseEnergyRegisterType;
	}

	@Override
	public int reverseEnergyAddress() {
		return this.builder.reverseEnergyAddress;
	}

	@Override
	public Encoding reverseEnergyEncoding() {
		return this.builder.reverseEnergyEncoding;
	}

	@Override
	public WordOrder reverseEnergyWordOrder() {
		return this.builder.reverseEnergyWordOrder;
	}

	@Override
	public ScaleFactor reverseEnergyScaleFactor() {
		return this.builder.reverseEnergyScaleFactor;
	}

}
