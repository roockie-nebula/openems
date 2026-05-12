package io.openems.edge.meter.sofar;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.bridge.modbus.api.ModbusUtils.FunctionCode.FC3;
import static io.openems.edge.bridge.modbus.api.ModbusUtils.readElementOnce;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.ModbusUtils;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Sofar", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences("Modbus")
public class MeterSofarImpl extends AbstractOpenemsModbusComponent
		implements MeterSofar, ElectricityMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	private final Logger log = LoggerFactory.getLogger(MeterSofarImpl.class);

	private Config config = null;
	private String serial;

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public MeterSofarImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterSofar.ChannelId.values() //
		);

		ElectricityMeter.calculateSumCurrentFromPhases(this);
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.config.type();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var protocol = new ModbusProtocol(this, //
				new FC3ReadRegistersTask(0x484, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(0x484), SCALE_FACTOR_2), //
						m(ElectricityMeter.ChannelId.ACTIVE_POWER, new UnsignedWordElement(0x485), SCALE_FACTOR_2), //
						m(ElectricityMeter.ChannelId.REACTIVE_POWER, new UnsignedWordElement(0x486), SCALE_FACTOR_2)), //
				new FC3ReadRegistersTask(0x48D, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.VOLTAGE_L1, new UnsignedWordElement(0x48D), SCALE_FACTOR_3), //
						m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedWordElement(0x48E), SCALE_FACTOR_2), //
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, new SignedWordElement(0x48F), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2)), //
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, new SignedWordElement(0x490), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2))), //
				new FC3ReadRegistersTask(0x498, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.VOLTAGE_L2, new UnsignedWordElement(0x498), SCALE_FACTOR_3), //
						m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedWordElement(0x499), SCALE_FACTOR_2), //
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, new SignedWordElement(0x49A), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2)), //
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, new SignedWordElement(0x49B), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2))), //
				new FC3ReadRegistersTask(0x4A3, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.VOLTAGE_L3, new UnsignedWordElement(0x4A3), SCALE_FACTOR_3), //
						m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedWordElement(0x4A4), SCALE_FACTOR_2), //
						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, new SignedWordElement(0x4A5), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2)), //
						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, new SignedWordElement(0x4A6), //
								chain(INVERT_IF_TRUE(this.config.invert()), SCALE_FACTOR_2))) //
		);

		var consumption = !this.config.invert() //
				? ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY //
				: ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY;
		var production = !this.config.invert() //
				? ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY //
				: ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY;

		if (this.config.type() == MeterType.PRODUCTION) {
			protocol.addTask(new FC3ReadRegistersTask(0x686, Priority.LOW, //
					m(production, new UnsignedDoublewordElement(0x686), SCALE_FACTOR_MINUS_1), //
					new DummyRegisterElement(0x688, 0x689), //
					m(consumption, new UnsignedDoublewordElement(0x68A), SCALE_FACTOR_MINUS_1)));
		} else {
			protocol.addTask(new FC3ReadRegistersTask(0x68E, Priority.LOW, //
					m(production, new UnsignedDoublewordElement(0x68E), SCALE_FACTOR_MINUS_1), //
					new DummyRegisterElement(0x690, 0x691), //
					m(consumption, new UnsignedDoublewordElement(0x692), SCALE_FACTOR_MINUS_1)));
		}

		readElementOnce(FC3, protocol, ModbusUtils::retryOnNull, new StringWordElement(0x445, 8)) //
				.thenAccept(value -> {
					this.serial = TypeUtils.<String>getAsType(OpenemsType.STRING, value);
					if (this.serial == null) {
						this.logWarn(this.log, "Serial: null");
					} else {
						this.logInfo(this.log, "Serial: " + this.serial);
					}
				});

		return protocol;
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode) //
		);
	}
}
