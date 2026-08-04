package io.openems.edge.meter.modbus;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;

import java.util.ArrayList;
import java.util.List;

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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.modbus.ProtocolBuilder.Point;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

/**
 * Reads an active power and a cumulative active energy value from an arbitrary
 * Modbus register pair on an arbitrary Modbus device.
 *
 * <p>
 * Every device-specific detail - function code, address, encoding, word order,
 * scale factor - is configuration, so a new installation is a configuration
 * change rather than a firmware release.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Modbus", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class MeterModbusImpl extends AbstractOpenemsModbusComponent implements MeterModbus, ElectricityMeter,
		ModbusComponent, OpenemsComponent, TimedataProvider, EventHandler {

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata;

	private Config config;

	/** Non-null only while {@code energySource} is FROM_POWER. */
	private CalculateEnergyFromPower calculateConsumptionEnergy;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public MeterModbusImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterModbus.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		// Validate here rather than in defineModbusProtocol(), which cannot throw and
		// only runs once the Modbus bridge binds.
		ProtocolBuilder.validate(toPoints(config));
		this.calculateConsumptionEnergy = switch (config.energySource()) {
		case FROM_POWER -> new CalculateEnergyFromPower(this, //
				ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, this.componentManager.getClock());
		case REGISTER, NONE -> null;
		};
		// super.activate(...) returns true while the Modbus bridge reference is not yet
		// bound; the protocol is (re)built once it becomes available, so stop early here.
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
		return this.config.type();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var tasks = ProtocolBuilder.build(toPoints(this.config), this.config.priority(), this::m);
		return new ModbusProtocol(this, tasks.toArray(Task[]::new));
	}

	/**
	 * Translates the configuration into the values to read.
	 *
	 * <p>
	 * Energy is always mapped to the consumption channel and never inverted: every
	 * device in scope exposes a single monotonic counter, which cannot be split into
	 * import and export.
	 *
	 * @param config the {@link Config}
	 * @return the {@link Point}s
	 */
	private static List<Point> toPoints(Config config) {
		var points = new ArrayList<Point>();
		points.add(new Point(ElectricityMeter.ChannelId.ACTIVE_POWER, //
				config.powerFunctionCode(), config.powerAddress(), config.powerEncoding(), config.powerWordOrder(),
				ProtocolBuilder.converter(config.powerScaleFactor(), config.invertPower())));
		if (config.energySource() == EnergySource.REGISTER) {
			points.add(new Point(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
					config.energyFunctionCode(), config.energyAddress(), config.energyEncoding(),
					config.energyWordOrder(), ProtocolBuilder.converter(config.energyScaleFactor(), false)));
		}
		return points;
	}

	/**
	 * Integrates the active power into the cumulative consumption energy.
	 *
	 * <p>
	 * Negative power is clamped to zero: the target Channel is a cumulative
	 * consumption counter and must not run backwards.
	 */
	private void calculateEnergy() {
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			this.calculateConsumptionEnergy.update(null);
		} else {
			this.calculateConsumptionEnergy.update(Math.max(0, activePower));
		}
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || this.calculateConsumptionEnergy == null) {
			return;
		}
		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE -> this.calculateEnergy();
		}
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public String debugLog() {
		return this.alias() + ":" + this.getActivePower().asString();
	}
}
