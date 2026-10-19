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
 * Reads an active power and cumulative active energy values from arbitrary
 * Modbus registers on an arbitrary Modbus device.
 *
 * <p>
 * Every device-specific detail - register type, address, encoding, word order,
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

	/** Non-null only while {@code energySource} is INTEGRATE_FROM_POWER. */
	private CalculateEnergyFromPower calculateProductionEnergy;

	/** Non-null only while {@code energySource} is INTEGRATE_FROM_POWER. */
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
		if (config.powerSource() == PowerSource.NOT_AVAILABLE
				&& config.energySource() != EnergySource.READ_FROM_REGISTER) {
			throw new OpenemsException("Power source [NOT_AVAILABLE] requires Energy source [READ_FROM_REGISTER], got ["
					+ config.energySource() + "]");
		}
		ProtocolBuilder.validate(toPoints(config));
		if (config.energySource() == EnergySource.INTEGRATE_FROM_POWER) {
			var clock = this.componentManager.getClock();
			this.calculateProductionEnergy = new CalculateEnergyFromPower(this,
					ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, clock);
			this.calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
					ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, clock);
		} else {
			this.calculateProductionEnergy = null;
			this.calculateConsumptionEnergy = null;
		}
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
	 * Energy follows the OpenEMS {@link ElectricityMeter} convention for every
	 * {@link MeterType}: the counter that grows while the active power is positive
	 * maps to {@link ElectricityMeter.ChannelId#ACTIVE_PRODUCTION_ENERGY}, the one
	 * that grows while it is negative to
	 * {@link ElectricityMeter.ChannelId#ACTIVE_CONSUMPTION_ENERGY}. For a grid meter
	 * that is buy-from-grid and sell-to-grid respectively.
	 *
	 * @param config the {@link Config}
	 * @return the {@link Point}s
	 */
	private static List<Point> toPoints(Config config) {
		var points = new ArrayList<Point>();
		if (config.powerSource() == PowerSource.READ_FROM_REGISTER) {
			points.add(new Point(ElectricityMeter.ChannelId.ACTIVE_POWER, //
					config.powerRegisterType(), config.powerAddress(), config.powerEncoding(), config.powerWordOrder(),
					ProtocolBuilder.converter(config.powerScaleFactor(), config.powerInvert())));
		}
		if (config.energySource() == EnergySource.READ_FROM_REGISTER) {
			points.add(new Point(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, //
					config.energyRegisterType(), config.energyAddress(), config.energyEncoding(),
					config.energyWordOrder(), ProtocolBuilder.converter(config.energyScaleFactor(), false)));
			if (config.reverseEnergyEnabled()) {
				points.add(new Point(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, //
						config.reverseEnergyRegisterType(), config.reverseEnergyAddress(),
						config.reverseEnergyEncoding(), config.reverseEnergyWordOrder(),
						ProtocolBuilder.converter(config.reverseEnergyScaleFactor(), false)));
			}
		}
		return points;
	}

	/**
	 * Integrates the active power into the cumulative energy channels: positive
	 * power into the production energy, negative power into the consumption
	 * energy, so neither counter runs backwards.
	 */
	private void calculateEnergy() {
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			this.calculateProductionEnergy.update(null);
			this.calculateConsumptionEnergy.update(null);
		} else if (activePower >= 0) {
			this.calculateProductionEnergy.update(activePower);
			this.calculateConsumptionEnergy.update(0);
		} else {
			this.calculateProductionEnergy.update(0);
			this.calculateConsumptionEnergy.update(-activePower);
		}
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled() || this.calculateProductionEnergy == null) {
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
