package io.openems.edge.meter.huawei.sdongle;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
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
@GenerateTargetsFromReferences("Modbus")
public class MeterHuaweiSDongleImpl extends AbstractOpenemsModbusComponent
		implements MeterHuaweiSDongle, ElectricityMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	private final Logger log = LoggerFactory.getLogger(MeterHuaweiSDongleImpl.class);

	private String serial;

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public MeterHuaweiSDongleImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterHuaweiSDongle.ChannelId.values() //
		);

		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);

		ElectricityMeter.calculatePhasesFromActivePower(this);
		ElectricityMeter.calculatePhasesFromReactivePower(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());
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
				new FC3ReadRegistersTask(32069, Priority.LOW, //
						m(ElectricityMeter.ChannelId.VOLTAGE_L1, new UnsignedWordElement(32069), SCALE_FACTOR_MINUS_1), //
						m(ElectricityMeter.ChannelId.VOLTAGE_L2, new UnsignedWordElement(32070), SCALE_FACTOR_MINUS_1), //
						m(ElectricityMeter.ChannelId.VOLTAGE_L3, new UnsignedWordElement(32071), SCALE_FACTOR_MINUS_1), //
						m(ElectricityMeter.ChannelId.CURRENT_L1, new SignedDoublewordElement(32072), SCALE_FACTOR_MINUS_3), //
						m(ElectricityMeter.ChannelId.CURRENT_L2, new SignedDoublewordElement(32074), SCALE_FACTOR_MINUS_3), //
						m(ElectricityMeter.ChannelId.CURRENT_L3, new SignedDoublewordElement(32076), SCALE_FACTOR_MINUS_3), //
						new DummyRegisterElement(32078, 32079), //
						m(ElectricityMeter.ChannelId.ACTIVE_POWER, new SignedDoublewordElement(32080)), //
						m(ElectricityMeter.ChannelId.REACTIVE_POWER, new SignedDoublewordElement(32082)), //
						new DummyRegisterElement(32084, 32084), //
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(32085), SCALE_FACTOR_2), //
						new DummyRegisterElement(32086, 32105), //
						m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new SignedDoublewordElement(32106),
								SCALE_FACTOR_1)));

		readElementOnce(FC3, protocol, ModbusUtils::retryOnNull, new StringWordElement(30015, 10)) //
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
		return "AP:" + this.getActivePower().asString() //
				+ " E:" + this.getActiveProductionEnergy().asString();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode) //
		);
	}
}
