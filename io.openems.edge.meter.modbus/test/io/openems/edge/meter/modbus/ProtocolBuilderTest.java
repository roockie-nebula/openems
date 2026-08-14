package io.openems.edge.meter.modbus;

import static io.openems.common.utils.ReflectionUtils.getValueViaReflection;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.DIRECT_1_TO_1;
import static io.openems.edge.meter.modbus.Encoding.FLOAT32;
import static io.openems.edge.meter.modbus.Encoding.INT16;
import static io.openems.edge.meter.modbus.Encoding.INT32;
import static io.openems.edge.meter.modbus.Encoding.UINT16;
import static io.openems.edge.meter.modbus.Encoding.UINT32;
import static io.openems.edge.meter.modbus.RegisterType.HOLDING_REGISTERS;
import static io.openems.edge.meter.modbus.RegisterType.INPUT_REGISTERS;
import static io.openems.edge.meter.modbus.ScaleFactor.HUNDRED;
import static io.openems.edge.meter.modbus.ScaleFactor.ONE;
import static io.openems.edge.meter.modbus.ScaleFactor.ONE_HUNDREDTH;
import static io.openems.edge.meter.modbus.ScaleFactor.ONE_TENTH;
import static io.openems.edge.meter.modbus.ScaleFactor.ONE_THOUSANDTH;
import static io.openems.edge.meter.modbus.ScaleFactor.TEN;
import static io.openems.edge.meter.modbus.ScaleFactor.THOUSAND;
import static io.openems.edge.meter.modbus.WordOrder.HIGH_WORD_FIRST;
import static io.openems.edge.meter.modbus.WordOrder.LOW_WORD_FIRST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.modbus.ProtocolBuilder.Point;

public class ProtocolBuilderTest {

	private static final ElectricityMeter.ChannelId POWER = ElectricityMeter.ChannelId.ACTIVE_POWER;
	private static final ElectricityMeter.ChannelId ENERGY = ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY;

	/** A mapper that registers nothing, so no component is needed. */
	private static final ProtocolBuilder.ElementMapper NO_MAPPING = (channelId, element, converter) -> element;

	private static Point point(ElectricityMeter.ChannelId channelId, RegisterType registerType, int address,
			Encoding encoding) {
		return new Point(channelId, registerType, address, encoding, HIGH_WORD_FIRST, DIRECT_1_TO_1);
	}

	private static List<Task> build(Point... points) {
		return ProtocolBuilder.build(List.of(points), Priority.LOW, NO_MAPPING);
	}

	/*
	 * Encodings.
	 */

	@Test
	public void everyEncodingProducesItsElementAndLength() {
		assertEquals(SignedWordElement.class, INT16.createElement(0, HIGH_WORD_FIRST).getClass());
		assertEquals(UnsignedWordElement.class, UINT16.createElement(0, HIGH_WORD_FIRST).getClass());
		assertEquals(SignedDoublewordElement.class, INT32.createElement(0, HIGH_WORD_FIRST).getClass());
		assertEquals(UnsignedDoublewordElement.class, UINT32.createElement(0, HIGH_WORD_FIRST).getClass());
		assertEquals(FloatDoublewordElement.class, FLOAT32.createElement(0, HIGH_WORD_FIRST).getClass());

		assertEquals(1, INT16.createElement(0, HIGH_WORD_FIRST).length);
		assertEquals(1, UINT16.createElement(0, HIGH_WORD_FIRST).length);
		assertEquals(2, INT32.createElement(0, HIGH_WORD_FIRST).length);
		assertEquals(2, UINT32.createElement(0, HIGH_WORD_FIRST).length);
		assertEquals(2, FLOAT32.createElement(0, HIGH_WORD_FIRST).length);

		assertEquals(1, INT16.getRegisterCount());
		assertEquals(2, FLOAT32.getRegisterCount());
	}

	@Test
	public void wordOrderIsAppliedToDoublewordElements() throws Exception {
		// Also pins the translation to the bridge's own enum, which is the one place
		// LOW_WORD_FIRST and LSWMSW have to line up.
		for (var encoding : List.of(INT32, UINT32, FLOAT32)) {
			assertEquals(encoding.toString(), io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW,
					getValueViaReflection(encoding.createElement(0, LOW_WORD_FIRST), "wordOrder"));
			assertEquals(encoding.toString(), io.openems.edge.bridge.modbus.api.element.WordOrder.MSWLSW,
					getValueViaReflection(encoding.createElement(0, HIGH_WORD_FIRST), "wordOrder"));
		}
	}

	@Test
	public void singleWordElementsHaveNoWordOrder() {
		// A single register cannot be reordered; the element type has no such field.
		for (var encoding : List.of(INT16, UINT16)) {
			var element = encoding.createElement(0, LOW_WORD_FIRST);
			assertThrows(encoding.toString(), Exception.class,
					() -> getValueViaReflection(element, "wordOrder"));
		}
	}

	/*
	 * Converters.
	 */

	@Test
	public void scaleFactorOneSkipsTheConverter() {
		assertSame(DIRECT_1_TO_1, ProtocolBuilder.converter(ONE, false));
		assertNotSame(DIRECT_1_TO_1, ProtocolBuilder.converter(ONE, true));
		assertNotSame(DIRECT_1_TO_1, ProtocolBuilder.converter(HUNDRED, false));
	}

	@Test
	public void everyScaleFactorMultipliesByItsOwnFactor() {
		assertEquals(1, ProtocolBuilder.converter(ONE_THOUSANDTH, false).elementToChannel(1000));
		assertEquals(10, ProtocolBuilder.converter(ONE_HUNDREDTH, false).elementToChannel(1000));
		assertEquals(100, ProtocolBuilder.converter(ONE_TENTH, false).elementToChannel(1000));
		assertEquals(1000, ProtocolBuilder.converter(ONE, false).elementToChannel(1000));
		assertEquals(10_000, ProtocolBuilder.converter(TEN, false).elementToChannel(1000));
		assertEquals(100_000, ProtocolBuilder.converter(HUNDRED, false).elementToChannel(1000));
		assertEquals(1_000_000, ProtocolBuilder.converter(THOUSAND, false).elementToChannel(1000));
	}

	@Test
	public void invertAppliesOnTopOfTheScaleFactor() {
		assertEquals(-100_000, ProtocolBuilder.converter(HUNDRED, true).elementToChannel(1000));
		assertEquals(-1000, ProtocolBuilder.converter(ONE, true).elementToChannel(1000));
	}

	/*
	 * Task merging.
	 */

	@Test
	public void mergesAContiguousPairWithEnergyFirst() {
		// Scheco: energy 9206, power 9208
		var tasks = build(//
				point(ENERGY, HOLDING_REGISTERS, 9206, INT32), //
				point(POWER, HOLDING_REGISTERS, 9208, INT32));

		assertEquals(1, tasks.size());
		var task = tasks.get(0);
		assertEquals(FC3ReadRegistersTask.class, task.getClass());
		assertEquals(9206, task.getStartAddress());
		assertEquals(4, task.getLength());
		assertEquals(2, task.getElements().length);
	}

	@Test
	public void mergesAContiguousPairWithPowerFirst() {
		// Simaka WMZ: power 24, energy 26. Merging must not assume an order.
		var tasks = build(//
				point(POWER, HOLDING_REGISTERS, 24, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 26, INT32));

		assertEquals(1, tasks.size());
		var task = tasks.get(0);
		assertEquals(24, task.getStartAddress());
		assertEquals(4, task.getLength());
	}

	@Test
	public void mergesEvenWhenThePointsAreGivenOutOfOrder() {
		// The higher address is listed first; the Task must still start at the lower.
		var tasks = build(//
				point(POWER, HOLDING_REGISTERS, 26, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 24, INT32));

		assertEquals(1, tasks.size());
		assertEquals(24, tasks.get(0).getStartAddress());
	}

	@Test
	public void fillsAnInteriorGapWithADummyElement() {
		// span 0..7 = 8 registers, exactly at the merge limit
		var tasks = build(//
				point(POWER, HOLDING_REGISTERS, 0, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 6, INT32));

		assertEquals(1, tasks.size());
		var elements = tasks.get(0).getElements();
		assertEquals(3, elements.length);
		assertEquals(DummyRegisterElement.class, elements[1].getClass());
		assertEquals(2, elements[1].startAddress);
		assertEquals(4, elements[1].length);
		assertEquals(8, tasks.get(0).getLength());
	}

	@Test
	public void splitsWhenTheSpanExceedsTheMergeLimit() {
		// span 0..9 = 10 registers
		var tasks = build(//
				point(POWER, HOLDING_REGISTERS, 0, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 8, INT32));

		assertEquals(2, tasks.size());
		assertEquals(0, tasks.get(0).getStartAddress());
		assertEquals(2, tasks.get(0).getLength());
		assertEquals(8, tasks.get(1).getStartAddress());
		assertEquals(2, tasks.get(1).getLength());
	}

	@Test
	public void splitsOnDifferingRegisterTypesRegardlessOfSpan() {
		var tasks = build(//
				point(POWER, INPUT_REGISTERS, 2, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 0, INT32));

		assertEquals(2, tasks.size());
		assertEquals(FC3ReadRegistersTask.class, tasks.get(0).getClass());
		assertEquals(0, tasks.get(0).getStartAddress());
		assertEquals(FC4ReadInputRegistersTask.class, tasks.get(1).getClass());
		assertEquals(2, tasks.get(1).getStartAddress());
	}

	@Test
	public void aSinglePointProducesASingleTask() {
		// energySource = NONE or FROM_POWER: power only
		var tasks = build(point(POWER, INPUT_REGISTERS, 55, FLOAT32));

		assertEquals(1, tasks.size());
		assertEquals(FC4ReadInputRegistersTask.class, tasks.get(0).getClass());
		assertEquals(55, tasks.get(0).getStartAddress());
		assertEquals(2, tasks.get(0).getLength());
	}

	@Test
	public void elementsAreAscendingAndGapless() {
		// AbstractTask throws if they are not, so reaching this point already proves it;
		// assert explicitly so a regression names the actual problem.
		for (var task : build(//
				point(POWER, HOLDING_REGISTERS, 0, INT16), //
				point(ENERGY, HOLDING_REGISTERS, 5, INT32))) {
			var next = task.getStartAddress();
			for (ModbusElement element : task.getElements()) {
				assertEquals(next, element.startAddress);
				next += element.length;
			}
			assertEquals(task.getStartAddress() + task.getLength(), next);
		}
	}

	/*
	 * Validation.
	 */

	@Test
	public void validateAcceptsTheKnownDevices() throws Exception {
		ProtocolBuilder.validate(List.of(//
				point(ENERGY, HOLDING_REGISTERS, 9206, INT32), //
				point(POWER, HOLDING_REGISTERS, 9208, INT32)));
		ProtocolBuilder.validate(List.of(//
				point(ENERGY, INPUT_REGISTERS, 53, FLOAT32), //
				point(POWER, INPUT_REGISTERS, 55, FLOAT32)));
		ProtocolBuilder.validate(List.of(point(POWER, HOLDING_REGISTERS, ProtocolBuilder.MAX_ADDRESS, INT16)));
	}

	@Test
	public void validateRejectsAnOutOfRangeAddress() {
		assertMessageContains("must be within", () -> ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, -1, INT32))));
		assertMessageContains("must be within", () -> ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, ProtocolBuilder.MAX_ADDRESS + 1, INT32))));
	}

	@Test
	public void validateRejectsAnElementRunningPastTheLastRegister() {
		assertMessageContains("runs past", () -> ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, ProtocolBuilder.MAX_ADDRESS, INT32))));
	}

	@Test
	public void validateRejectsOverlappingPoints() {
		assertMessageContains("overlap", () -> ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, 100, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 101, INT32))));
		// identical addresses
		assertMessageContains("overlap", () -> ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, 100, INT32), //
				point(ENERGY, HOLDING_REGISTERS, 100, INT16))));
	}

	@Test
	public void validateAllowsTheSameAddressOnDifferentRegisterTypes() throws Exception {
		// Holding register 100 and input register 100 are different registers.
		ProtocolBuilder.validate(List.of(//
				point(POWER, HOLDING_REGISTERS, 100, INT32), //
				point(ENERGY, INPUT_REGISTERS, 100, INT32)));
	}

	private static void assertMessageContains(String expected, ThrowingRunnable runnable) {
		var thrown = assertThrows(OpenemsException.class, runnable);
		assertTrue("Expected message to contain [" + expected + "], got [" + thrown.getMessage() + "]",
				thrown.getMessage().contains(expected));
	}
}
