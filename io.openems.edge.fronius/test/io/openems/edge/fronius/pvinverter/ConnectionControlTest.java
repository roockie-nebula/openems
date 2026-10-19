package io.openems.edge.fronius.pvinverter;

import static io.openems.common.utils.ReflectionUtils.getValueViaReflection;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static io.openems.edge.fronius.pvinverter.PvInverterFronius.ChannelId.CONNECTION_COMMAND_REJECTED;
import static io.openems.edge.fronius.pvinverter.PvInverterFronius.ChannelId.CONNECTION_CONTROL_AVAILABLE;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_POWER;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.osgi.service.event.Event;

import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.procimg.Register;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task.ExecuteState;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;

public class ConnectionControlTest {

	@Test
	public void discoversOnlyConnectionPointsWithoutShiftingFollowingModel() throws Exception {
		try (var fixture = new Fixture(false, true, true)) {
			assertTrue(fixture.sut.isSunSpecInitializationCompleted());
			assertEquals(AccessMode.WRITE_ONLY, fixture.sut.getSetConnectionChannel().channelDoc().getAccessMode());
			for (var name : List.of("S123ConnWinTms", "S123ConnRvrtTms", "S123Conn")) {
				assertEquals(AccessMode.READ_ONLY, fixture.sut.channel(name).channelDoc().getAccessMode());
			}
			assertEquals(7, fixture.sut.channel("S123ConnWinTms").getNextValue().get());
			assertEquals(8, fixture.sut.channel("S123ConnRvrtTms").getNextValue().get());
			assertEquals(1, fixture.sut.channel("S123Conn").getNextValue().get());
			assertEquals(0F, fixture.sut.channel("S113W").getNextValue().get());
			assertFalse(fixture.sut.channels().stream().anyMatch(c -> c.channelId().id().startsWith("S123WMax")));
			assertEquals(1, fixture.protocol().getTaskManager().getTasks().stream()
					.filter(FC16WriteRegistersTask.class::isInstance)
					.filter(task -> task.getStartAddress() >= 40070 && task.getStartAddress() < 40096).count());
			assertEquals(true, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			assertTrue(fixture.bridge.writes.isEmpty());
		}
	}

	@Test
	public void writesExactOffAndOnPayloadAndNeverReversesWithoutCommand() throws Exception {
		try (var fixture = new Fixture(false, true, true)) {
			for (var value : new int[] { 0, 1, 0 }) {
				fixture.command(value);
				assertEquals(ExecuteState.OK, fixture.writeTask().execute(fixture.bridge));
				var request = fixture.bridge.writes.getLast();
				assertEquals(40072, request.getReference());
				assertArrayEquals(new int[] { 0, 0, value },
						Arrays.stream(request.getRegisters()).mapToInt(Register::getValue).toArray());
				assertTrue(fixture.sut.getSetConnectionChannel().getNextWriteValue().isEmpty());
			}
			for (var cycle = 0; cycle < 65; cycle++) {
				fixture.cycle(); // Expired API overrides supply no further command.
				assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			}
			assertEquals(3, fixture.bridge.writes.size());
			fixture.test.next(new TestCase());
			assertEquals(0, fixture.sut.channel("S123Conn").getNextValue().get());
		}
	}

	@Test
	public void invalidCommandsAndAbsentCommandsClearUnsentValues() throws Exception {
		try (var fixture = new Fixture(false, true, true)) {
			for (var invalid : new int[] { -1, 2, 65535 }) {
				fixture.command(0); // Simulate a write skipped during bridge backoff.
				fixture.command(invalid);
				assertEquals(true, fixture.sut.channel(CONNECTION_COMMAND_REJECTED).getNextValue().get());
				assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			}
			fixture.command(0);
			fixture.cycle();
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			assertTrue(fixture.bridge.writes.isEmpty());
		}
	}

	@Test
	public void readOnlyAndDisabledComponentsRejectCommands() throws Exception {
		try (var fixture = new Fixture(true, true, true)) {
			fixture.command(0);
			assertEquals(false, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertEquals(true, fixture.sut.channel(CONNECTION_COMMAND_REJECTED).getNextValue().get());
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
		}
		try (var fixture = new Fixture(false, false, true)) {
			fixture.command(1);
			assertEquals(false, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertEquals(true, fixture.sut.channel(CONNECTION_COMMAND_REJECTED).getNextValue().get());
			assertTrue(fixture.sut.getSetConnectionChannel().getNextWriteValue().isEmpty());
			assertTrue(fixture.bridge.writes.isEmpty());
		}
	}

	@Test
	public void missingModelAndUndefinedFieldsPreventWrites() throws Exception {
		try (var fixture = new Fixture(false, true, false)) {
			assertTrue(fixture.sut.isSunSpecInitializationCompleted());
			fixture.command(0);
			assertNull(fixture.writeTask());
			assertEquals(false, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertEquals(true, fixture.sut.channel(CONNECTION_COMMAND_REJECTED).getNextValue().get());
		}
		try (var fixture = new Fixture(false, true, true)) {
			fixture.sut.channel("S123ConnWinTms").setNextValue(null);
			fixture.command(0);
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			fixture.sut.channel("S123ConnWinTms").setNextValue(0);
			fixture.sut.channel("S123Conn").setNextValue(2);
			fixture.command(1);
			assertNull(fixture.sut.channel("S123Conn").getNextValue().get());
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
		}
	}

	@Test
	public void communicationFailureInvalidatesReadbackAndClearsUnsentCommand() throws Exception {
		try (var fixture = new Fixture(false, true, true)) {
			fixture.command(0);
			for (var i = 0; i < 12; i++) {
				fixture.sut._setModbusCommunicationFailed(true);
			}
			fixture.command(1);
			assertEquals(false, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertNull(fixture.sut.channel("S123Conn").getNextValue().get());
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			fixture.sut.getModbusCommunicationFailedChannel().nextProcessImage();
			for (var i = 0; i < 12; i++) {
				fixture.sut._setModbusCommunicationFailed(false);
			}
			fixture.sut.channel("S123Conn").setNextValue(1); // Fresh inverter readback.
			fixture.cycle();
			assertEquals(true, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
		}
	}

	@Test
	public void reinitializationAndDeactivationRemovePrivateTaskAndPendingWrites() throws Exception {
		try (var fixture = new Fixture(false, true, true)) {
			fixture.command(0);
			var previous = fixture.writeTask();
			fixture.sut.reinitializeSunSpecChannels();
			assertNull(fixture.writeTask());
			assertFalse(fixture.protocol().getTaskManager().getTasks().contains(previous));
			assertEquals(ExecuteState.NO_OP, previous.execute(fixture.bridge));
			for (var i = 0; i < 10; i++) {
				fixture.test.next(new TestCase());
			}
			assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(fixture.bridge));
			fixture.command(0);
			previous = fixture.writeTask();
			fixture.test.deactivate();
			assertNull(fixture.writeTask());
			assertFalse(fixture.protocol().getTaskManager().getTasks().contains(previous));
			assertEquals(ExecuteState.NO_OP, previous.execute(fixture.bridge));
		}
	}

	@Test
	public void readsScaledIntegerMeasurementsInReadOnlyMode() throws Exception {
		var bridge = measurementBridge(103);
		bridge.withRegisters(40083, 12340, 0xFFFF) // 12340 * 10^-1 W
				.withRegisters(40093, 0x8000, 0, 1); // Unsigned 32-bit energy * 10^1 Wh
		try (var fixture = new Fixture(true, true, bridge)) {
			assertEquals(1234, fixture.sut.channel(ACTIVE_POWER).getNextValue().get());
			assertEquals(21474836480L, fixture.sut.channel(ACTIVE_PRODUCTION_ENERGY).getNextValue().get());
			assertEquals(false, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertTrue(bridge.writes.isEmpty());

			bridge.withRegisters(40083, 0xFF85, 1) // -123 * 10^1 W; scale factors can change.
					.withRegisters(40093, 0, 12340, 0xFFFF);
			fixture.test.next(new TestCase(), 2);
			assertEquals(-1230, fixture.sut.channel(ACTIVE_POWER).getNextValue().get());
			assertEquals(1234L, fixture.sut.channel(ACTIVE_PRODUCTION_ENERGY).getNextValue().get());
			assertTrue(bridge.writes.isEmpty());
		}
	}

	@Test
	public void preservesIntegerMeasurementsDuringConnectionControl() throws Exception {
		this.assertMeasurementsDuringConnectionControl(103);
	}

	@Test
	public void preservesFloatMeasurementsDuringConnectionControl() throws Exception {
		this.assertMeasurementsDuringConnectionControl(113);
	}

	private void assertMeasurementsDuringConnectionControl(int model) throws Exception {
		var bridge = measurementBridge(model);
		setMeasurements(bridge, model, 2500, 123456);
		try (var fixture = new Fixture(false, true, bridge)) {
			assertTrue(fixture.sut.isSunSpecInitializationCompleted());
			assertEquals(2500, fixture.sut.channel(ACTIVE_POWER).getNextValue().get());
			assertEquals(123456L, fixture.sut.channel(ACTIVE_PRODUCTION_ENERGY).getNextValue().get());
			assertEquals(true, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
			assertTrue(bridge.writes.isEmpty());

			for (var connection : new int[] { 0, 1 }) {
				fixture.command(connection);
				assertEquals(ExecuteState.OK, fixture.writeTask().execute(bridge));
				var request = bridge.writes.getLast();
				assertEquals(model == 103 ? 40229 : 40239, request.getReference());
				assertEquals(1, request.getUnitID());
				assertArrayEquals(new int[] { 0, 0, connection },
						Arrays.stream(request.getRegisters()).mapToInt(Register::getValue).toArray());

				// Simulate fresh inverter measurements after the command; energy stays cumulative.
				setMeasurements(bridge, model, connection * 2500, 123456);
				fixture.test.next(new TestCase(), 2);
				assertEquals(connection, fixture.sut.channel("S123Conn").getNextValue().get());
				assertEquals(connection * 2500, fixture.sut.channel(ACTIVE_POWER).getNextValue().get());
				assertEquals(123456L, fixture.sut.channel(ACTIVE_PRODUCTION_ENERGY).getNextValue().get());
				assertEquals(true, fixture.sut.channel(CONNECTION_CONTROL_AVAILABLE).getNextValue().get());
				assertEquals(ExecuteState.NO_OP, fixture.writeTask().execute(bridge));
			}
			assertEquals(2, bridge.writes.size());
		}
	}

	private static RecordingBridge measurementBridge(int model) {
		var bridge = new RecordingBridge();
		var measurementLength = model == 103 ? 50 : 60;
		// The installed GEN24 uses a 65-register common block, without the optional pad.
		bridge.withRegisters(40000, 0x5375, 0x6e53) //
				.withRegisters(40002, 1, 65).withRegisters(40004, new int[65]) //
				.withRegister(40068, 1) // Unit ID
				.withRegisters(40069, model, measurementLength).withRegisters(40071, new int[measurementLength]);
		var nextAddress = 40071 + measurementLength;
		for (var block : new int[][] { { 120, 26 }, { 121, 30 }, { 122, 44 } }) {
			bridge.withRegisters(nextAddress, block[0], block[1]);
			nextAddress += 2 + block[1];
		}
		bridge.withRegisters(nextAddress, 123, 24) //
				.withRegisters(nextAddress + 2, 0, 0, 1) //
				.withRegisters(nextAddress + 26, 0xFFFF, 0);
		return bridge;
	}

	private static void setMeasurements(RecordingBridge bridge, int model, int power, int energy) {
		if (model == 103) {
			bridge.withRegisters(40083, power, 0).withRegisters(40093, energy >>> 16, energy & 0xFFFF, 0);
		} else {
			var powerBits = Float.floatToIntBits(power);
			var energyBits = Float.floatToIntBits(energy);
			bridge.withRegisters(40091, powerBits >>> 16, powerBits & 0xFFFF) //
					.withRegisters(40101, energyBits >>> 16, energyBits & 0xFFFF);
		}
	}

	private static class Fixture implements AutoCloseable {
		private final PvInverterFroniusImpl sut = new PvInverterFroniusImpl();
		private final RecordingBridge bridge;
		private final ComponentTest test;

		private Fixture(boolean readOnly, boolean enabled, boolean connectionModel) throws Exception {
			this(readOnly, enabled, connectionBridge(connectionModel));
		}

		private Fixture(boolean readOnly, boolean enabled, RecordingBridge bridge) throws Exception {
			this.bridge = bridge;
			this.test = new ComponentTest(this.sut) //
					.addReference("setModbus", this.bridge) //
					.activate(MyConfig.create().setId("pvInverter0").setEnabled(enabled).setReadOnly(readOnly) //
							.setModbusId("modbus0").setModbusUnitId(1).build());
			this.test.next(new TestCase(), 10);
		}

		private static RecordingBridge connectionBridge(boolean connectionModel) {
			var bridge = new RecordingBridge();
			bridge.withRegisters(40000, 0x5375, 0x6e53) //
					.withRegisters(40002, 1, 66).withRegisters(40004, new int[66]) //
					.withRegisters(40070, connectionModel ? 123 : 999, 24) //
					.withRegisters(40072, 7, 8, 1) //
					.withRegisters(40096, 113, 60).withRegisters(40098, new int[60]) //
					.withRegisters(40158, 0xFFFF, 0);
			return bridge;
		}

		private void command(int connection) throws Exception {
			this.sut.getSetConnectionChannel().setNextWriteValue(connection);
			this.cycle();
		}

		private void cycle() {
			this.sut.handleEvent(new Event(TOPIC_CYCLE_EXECUTE_WRITE, (java.util.Dictionary<String, ?>) null));
		}

		private FC16WriteRegistersTask writeTask() {
			return getValueViaReflection(this.sut, "connectionWriteTask");
		}

		private ModbusProtocol protocol() {
			return getValueViaReflection(this.sut, "modbusProtocol");
		}

		@Override
		public void close() throws Exception {
			this.test.deactivate();
		}
	}

	private static class RecordingBridge extends DummyModbusBridge {
		private final List<WriteMultipleRegistersRequest> writes = new ArrayList<>();

		private RecordingBridge() {
			super("modbus0");
		}

		@Override
		public ModbusTransaction getNewModbusTransaction() throws OpenemsException {
			var delegate = super.getNewModbusTransaction();
			return new ModbusTransaction() {
				@Override
				public void execute() throws ModbusException {
					if (this.request instanceof WriteMultipleRegistersRequest write) {
						RecordingBridge.this.writes.add(write);
					}
					delegate.setRequest(this.request);
					delegate.execute();
					this.response = delegate.getResponse();
				}
			};
		}
	}
}
