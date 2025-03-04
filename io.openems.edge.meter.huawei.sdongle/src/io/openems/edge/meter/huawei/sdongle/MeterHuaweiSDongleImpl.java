package io.openems.edge.meter.huawei.sdongle;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ModbusUtils.readElementOnce;
import static io.openems.edge.bridge.modbus.api.ModbusUtils.FunctionCode.FC3;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.ModbusUtils;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
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
		name = "PvMeter.Huawei.SDongle", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"type=PRODUCTION" //
		})
public class MeterHuaweiSDongleImpl extends AbstractOpenemsModbusComponent implements MeterHuaweiSDongle, ElectricityMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	private final Logger log = LoggerFactory.getLogger(MeterHuaweiSDongle.class);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	private String serial;

	public MeterHuaweiSDongleImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterHuaweiSDongle.ChannelId.values() //
		);

		// Automatically calculate sum values from L1/L2/L3
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);

		// Automatically calculate L1/l2/L3 values from sum
		ElectricityMeter.calculatePhasesFromActivePower(this);
		ElectricityMeter.calculatePhasesFromReactivePower(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.PRODUCTION;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var protocol = new ModbusProtocol(this, //

				new FC3ReadRegistersTask(32069, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.VOLTAGE_L1, new UnsignedWordElement(32069), SCALE_FACTOR_MINUS_1), // Ua
						m(ElectricityMeter.ChannelId.VOLTAGE_L2, new UnsignedWordElement(32070), SCALE_FACTOR_MINUS_1), // Ub
						m(ElectricityMeter.ChannelId.VOLTAGE_L3, new UnsignedWordElement(32071), SCALE_FACTOR_MINUS_1), // Uc
						m(ElectricityMeter.ChannelId.CURRENT_L1, new SignedDoublewordElement(32072), SCALE_FACTOR_MINUS_3), // Ia
						m(ElectricityMeter.ChannelId.CURRENT_L2, new SignedDoublewordElement(32074), SCALE_FACTOR_MINUS_3), // Ib
						m(ElectricityMeter.ChannelId.CURRENT_L3, new SignedDoublewordElement(32076), SCALE_FACTOR_MINUS_3), // Ic
						new DummyRegisterElement(32078, 32079), // Active power peak of current	day
						m(ElectricityMeter.ChannelId.ACTIVE_POWER, new SignedDoublewordElement(32080)), // Active power
						m(ElectricityMeter.ChannelId.REACTIVE_POWER, new SignedDoublewordElement(32082)), // Reactive power
						new DummyRegisterElement(32084, 32084), // Power factor
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(32085), SCALE_FACTOR_2) // Frequency
				), //

				new FC3ReadRegistersTask(32106, Priority.HIGH, //
						m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new SignedDoublewordElement(32106), SCALE_FACTOR_1) // E-Total
				) //
		);

		// Read serial number once
		readElementOnce(FC3, protocol, ModbusUtils::retryOnNull, new StringWordElement(30015, 10)) //
				.thenAccept(value -> {
					this.serial = TypeUtils.<String>getAsType(OpenemsType.STRING, value);
					if (this.serial == null) {
						this.logWarn(this.log, "Serial: null");
					} else {
						this.logInfo(this.log, "Serial: " + this.serial);
					} //
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
