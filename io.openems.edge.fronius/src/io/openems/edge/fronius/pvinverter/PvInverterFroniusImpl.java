package io.openems.edge.fronius.pvinverter;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.Map;
import java.util.stream.Stream;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import com.google.common.collect.ImmutableMap;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.sunspec.DefaultSunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.SunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.pvinverter.AbstractSunSpecPvInverter;
import io.openems.edge.bridge.modbus.sunspec.pvinverter.SunSpecPvInverter;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.Phase.SingleOrAllPhase;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "PV-Inverter.Fronius", //
		immediate = true, //
		configurationPolicy = REQUIRE, //
		property = { //
				"type=PRODUCTION" //
		})
@EventTopics({ //
		TOPIC_CYCLE_EXECUTE_WRITE //
})
@GenerateTargetsFromReferences("Modbus")
public class PvInverterFroniusImpl extends AbstractSunSpecPvInverter implements PvInverterFronius, SunSpecPvInverter,
		ManagedSymmetricPvInverter, ElectricityMeter, ModbusComponent, OpenemsComponent, EventHandler, ModbusSlave {

	private static final Map<SunSpecModel, Priority> ACTIVE_MODELS = ImmutableMap.<SunSpecModel, Priority>builder()
			.put(DefaultSunSpecModel.S_1, Priority.LOW) // from 40002
			.put(DefaultSunSpecModel.S_103, Priority.HIGH)
			.put(DefaultSunSpecModel.S_111, Priority.LOW) // from 40070
			.put(DefaultSunSpecModel.S_112, Priority.LOW) // from 40070
			.put(DefaultSunSpecModel.S_113, Priority.HIGH) // from 40070
			.put(ConnectionModel.MODEL, Priority.HIGH)
			.build();

	private static final int READ_FROM_MODBUS_BLOCK = 1;

	private boolean readOnly = true;
	private FC16WriteRegistersTask connectionWriteTask;
	private UnsignedWordElement[] connectionWriteElements;

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public PvInverterFroniusImpl() {
		super(//
				ACTIVE_MODELS, //
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				ManagedSymmetricPvInverter.ChannelId.values(), //
				SunSpecPvInverter.ChannelId.values(), //
				PvInverterFronius.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.removeConnectionWriteTask();
		this.readOnly = config.readOnly();
		super.activate(context, config.id(), config.alias(), config.enabled(), config.readOnly(), config.modbusUnitId(),
				READ_FROM_MODBUS_BLOCK, SingleOrAllPhase.ALL);
	}

	@Deactivate
	protected void deactivate() {
		this.removeConnectionWriteTask();
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		super.handleEvent(event);
		if (event.getTopic().equals(TOPIC_CYCLE_EXECUTE_WRITE)) {
			this.handleConnectionCommand();
		}
	}

	@Override
	protected synchronized void addBlock(int startAddress, SunSpecModel model, Priority priority) {
		super.addBlock(startAddress, model, priority);
		if (model == ConnectionModel.MODEL) {
			this.removeConnectionWriteTask();
			this.connectionWriteElements = new UnsignedWordElement[] { //
					new UnsignedWordElement(startAddress + 2), //
					new UnsignedWordElement(startAddress + 3), //
					new UnsignedWordElement(startAddress + 4) };
			this.connectionWriteTask = new FC16WriteRegistersTask(startAddress + 2, this.connectionWriteElements);
			this.getModbusProtocol().addTask(this.connectionWriteTask);
		}
	}

	@Override
	protected synchronized void reinitializeSunSpecChannels() {
		if (this.isSunSpecInitializationCompleted()) {
			this.removeConnectionWriteTask();
		}
		super.reinitializeSunSpecChannels();
	}

	private synchronized void handleConnectionCommand() {
		final var command = this.getSetConnectionChannel().getNextWriteValueAndReset();
		this.clearConnectionWriteValues();

		var communicating = !this.getModbusCommunicationFailedChannel().getNextValue().orElse(true);
		this.getSunSpecChannel(ConnectionModel.CONN).ifPresent(channel -> {
			var value = channel.getNextValue().get();
			if (!communicating || !this.isEnabled()
					|| !Integer.valueOf(0).equals(value) && !Integer.valueOf(1).equals(value)) {
				channel.setNextValue(null);
			}
		});
		var available = this.isEnabled() && !this.readOnly && this.isSunSpecInitializationCompleted()
				&& this.connectionWriteTask != null && communicating && Stream.of(ConnectionModel.values())
						.allMatch(point -> this.getSunSpecChannel(point)
								.map(channel -> channel.getNextValue().isDefined()).orElse(false));
		this.channel(PvInverterFronius.ChannelId.CONNECTION_CONTROL_AVAILABLE).setNextValue(available);
		var rejected = command.isPresent() && (!available || command.get() != 0 && command.get() != 1);
		this.channel(PvInverterFronius.ChannelId.CONNECTION_COMMAND_REJECTED).setNextValue(rejected);
		if (command.isEmpty() || rejected) {
			return;
		}

		this.connectionWriteElements[0].setNextWriteValue(0);
		this.connectionWriteElements[1].setNextWriteValue(0);
		this.connectionWriteElements[2].setNextWriteValue(command.get());
	}

	private void clearConnectionWriteValues() {
		if (this.connectionWriteElements != null) {
			for (var element : this.connectionWriteElements) {
				element.getNextWriteValueAndReset();
			}
		}
	}

	private synchronized void removeConnectionWriteTask() {
		this.getSetConnectionChannel().getNextWriteValueAndReset();
		this.clearConnectionWriteValues();
		if (this.connectionWriteTask != null) {
			this.getModbusProtocol().removeTask(this.connectionWriteTask);
			this.connectionWriteTask = null;
			this.connectionWriteElements = null;
		}
		this.channel(PvInverterFronius.ChannelId.CONNECTION_CONTROL_AVAILABLE).setNextValue(false);
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
	}
}
