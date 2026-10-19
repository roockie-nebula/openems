package io.openems.edge.meter.modbus;

import static io.openems.common.types.MeterType.CONSUMPTION_METERED;
import static io.openems.common.types.MeterType.GRID;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_POWER;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY;
import static io.openems.edge.meter.modbus.Encoding.FLOAT32;
import static io.openems.edge.meter.modbus.Encoding.INT32;
import static io.openems.edge.meter.modbus.Encoding.UINT32;
import static io.openems.edge.meter.modbus.RegisterType.INPUT_REGISTERS;
import static io.openems.edge.meter.modbus.ScaleFactor.HUNDRED;
import static io.openems.edge.meter.modbus.ScaleFactor.THOUSAND;
import static io.openems.edge.meter.modbus.WordOrder.LOW_WORD_FIRST;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.Before;
import org.junit.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.timedata.test.DummyTimedata;

public class MeterModbusImplTest {

	private TimeLeapClock clock;
	private ComponentTest test;

	/**
	 * Pre-fills the register map with the known meter shapes.
	 *
	 * <p>
	 * Every 32-bit value is larger than 65535, so its high word is non-zero and the
	 * two word orders are distinguishable.
	 */
	@Before
	public void setup() throws Exception {
		this.clock = new TimeLeapClock(Instant.ofEpochSecond(1577836800) /* 1. January 2020 00:00:00 */,
				ZoneOffset.UTC);
		this.test = new ComponentTest(new MeterModbusImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(this.clock)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.addReference("setModbus", new DummyModbusBridge("modbus0") //
						// Holding registers 0-3: power 1000 W, then 999999 at the energy address
						.withRegisters(0, 0x0000, 0x03E8, 0x000F, 0x423F) //
						// Holding registers 24-27, Simaka WMZ, LOW_WORD_FIRST:
						// power 98765 = 0x000181CD, energy 7654321 = 0x0074CBB1
						.withRegisters(24, 0x81CD, 0x0001, 0xCBB1, 0x0074) //
						// Holding registers 40-43, AE_WMZ_5, captured from the live
						// controller at 192.168.22.121:503 on 14. August 2026:
						// power 0 W, energy 70331 kWh = 0x000112BB
						.withRegisters(40, 0x0000, 0x0000, 0x12BB, 0x0001) //
						// Holding registers 100-103: FLOAT32 -1.5 and +1.5
						.withRegisters(100, 0xBFC0, 0x0000, 0x3FC0, 0x0000) //
						// Holding registers 9206-9209, Scheco, HIGH_WORD_FIRST:
						// energy 1234567 = 0x0012D687, power 123456 = 0x0001E240
						.withRegisters(9206, 0x0012, 0xD687, 0x0001, 0xE240) //
						// Input registers 53-56, Simaka emeter, FLOAT32:
						// energy 1234.5 = 0x449A5000, power 4321.0 = 0x45870800
						.withInputRegisters(53, 0x449A, 0x5000, 0x4587, 0x0800) //
						// Holding registers 18088-18091, FäWa MZH grid meter 104.002, LOW_WORD_FIRST,
						// captured from the live gateway at 192.168.40.101 on 19. October 2026:
						// sell 1525642 = 0x0017478A, buy 11473048 = 0x00AF1098, both in 1/10 kWh
						.withRegisters(18088, 0x478A, 0x0017, 0x1098, 0x00AF));
	}

	/**
	 * The Scheco Wärmepumpe: holding registers, INT32, HIGH_WORD_FIRST, energy in
	 * 1/10 kWh.
	 *
	 * @return a {@link MyConfig.Builder}
	 */
	private static MyConfig.Builder scheco() {
		return MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setModbusUnitId(10) //
				.setType(CONSUMPTION_METERED) //
				.setPowerAddress(9208) //
				.setEnergyAddress(9206) //
				.setEnergyScaleFactor(HUNDRED);
	}

	@Test
	public void schecoDecodesBothValues() throws Exception {
		this.test.activate(scheco().build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 123_456) //
						// 1234567 * 100 = 123_456_700 Wh
						.output(ACTIVE_PRODUCTION_ENERGY, 123_456_700L));
	}

	@Test
	public void invertNegatesPowerButNotEnergy() throws Exception {
		this.test.activate(scheco().setPowerInvert(true).build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, -123_456) //
						.output(ACTIVE_PRODUCTION_ENERGY, 123_456_700L));
	}

	@Test
	public void lowPriorityStillReadsTheMergedPair() throws Exception {
		// The pair is one Modbus transaction, so the single low-priority read per cycle
		// covers both values.
		this.test.activate(scheco().setPriority(Priority.LOW).build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 123_456) //
						.output(ACTIVE_PRODUCTION_ENERGY, 123_456_700L));
	}

	@Test
	public void simakaWmzDecodesReversedWordOrder() throws Exception {
		// Power comes first here, energy second - the opposite of the Scheco layout.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(24) //
				.setPowerWordOrder(LOW_WORD_FIRST) //
				.setEnergyAddress(26) //
				.setEnergyWordOrder(LOW_WORD_FIRST) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 98_765) //
						.output(ACTIVE_PRODUCTION_ENERGY, 7_654_321L));
	}

	@Test
	public void simakaWmzDecodesACapturedFieldReading() throws Exception {
		// The same configuration we deploy for AE_WMZ_5, run against registers read off
		// the real controller - so a decode regression shows up as a wrong kWh value,
		// not just a wrong synthetic one. The unit id is ignored by that slave.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setModbusUnitId(1) //
				.setType(CONSUMPTION_METERED) //
				.setPowerAddress(40) //
				.setPowerWordOrder(LOW_WORD_FIRST) //
				.setEnergyAddress(42) //
				.setEnergyWordOrder(LOW_WORD_FIRST) //
				.setEnergyScaleFactor(THOUSAND) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 0) //
						// 70331 kWh = 70_331_000 Wh
						.output(ACTIVE_PRODUCTION_ENERGY, 70_331_000L));
	}

	@Test
	public void wrongWordOrderYieldsADifferentValue() throws Exception {
		// Guards the test above: with HIGH_WORD_FIRST the same registers decode to
		// garbage, so
		// the assertion really does exercise word order.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(24) //
				.setEnergySource(EnergySource.NOT_AVAILABLE) //
				.build()) //
				.next(new TestCase() //
						// 0x81CD0001 as a signed 32-bit integer
						.output(ACTIVE_POWER, -2_117_271_551));
	}

	@Test
	public void simakaEmeterDecodesFloat32FromInputRegisters() throws Exception {
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerRegisterType(INPUT_REGISTERS) //
				.setPowerAddress(55) //
				.setPowerEncoding(FLOAT32) //
				.setEnergyRegisterType(INPUT_REGISTERS) //
				.setEnergyAddress(53) //
				.setEnergyEncoding(FLOAT32) //
				.setEnergyScaleFactor(THOUSAND) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 4321) //
						// 1234.5 kWh = 1_234_500 Wh
						.output(ACTIVE_PRODUCTION_ENERGY, 1_234_500L));
	}

	@Test
	public void negativeFloat32PowerIsScaledCorrectly() throws Exception {
		// ElementToChannelScaleFactorConverter tests 'result >= Float.MIN_VALUE', and
		// Float.MIN_VALUE is the smallest *positive* float - so a negative result takes
		// the Double branch instead of the Float one. The Channel coerces either way,
		// but the path is untested upstream.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(100) //
				.setPowerEncoding(FLOAT32) //
				.setPowerScaleFactor(THOUSAND) //
				.setEnergySource(EnergySource.NOT_AVAILABLE) //
				.build()) //
				.next(new TestCase() //
						// -1.5 kW = -1500 W
						.output(ACTIVE_POWER, -1500));
	}

	@Test
	public void positiveFloat32PowerIsScaledCorrectly() throws Exception {
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(102) //
				.setPowerEncoding(FLOAT32) //
				.setPowerScaleFactor(THOUSAND) //
				.setEnergySource(EnergySource.NOT_AVAILABLE) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 1500));
	}

	@Test
	public void energySourceNotAvailableReadsNoEnergyRegister() throws Exception {
		// The energy address points at a populated register; a null Channel proves no
		// energy element was built.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(0) //
				.setEnergyAddress(2) //
				.setEnergySource(EnergySource.NOT_AVAILABLE) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 1000) //
						.output(ACTIVE_PRODUCTION_ENERGY, null));
	}

	@Test
	public void energySourceIntegrateFromPowerDoesNotReadTheRegister() throws Exception {
		// Same layout as above: 999999 sits at the energy address and must not appear.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(0) //
				.setEnergyAddress(2) //
				.setEnergySource(EnergySource.INTEGRATE_FROM_POWER) //
				.build()) //
				// First cycle initialises the counter from Timedata; nothing to integrate yet
				.next(new TestCase() //
						.output(ACTIVE_POWER, 1000) //
						.output(ACTIVE_PRODUCTION_ENERGY, null)) //
				// 1000 W for one hour = 1000 Wh
				.next(new TestCase() //
						.timeleap(this.clock, 1, ChronoUnit.HOURS) //
						.output(ACTIVE_PRODUCTION_ENERGY, 1000L));
	}

	@Test
	public void integrateFromPowerCountsNegativePowerAsConsumption() throws Exception {
		// -1.5 kW at holding register 100
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(100) //
				.setPowerEncoding(FLOAT32) //
				.setPowerScaleFactor(THOUSAND) //
				.setEnergySource(EnergySource.INTEGRATE_FROM_POWER) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, -1500)) //
				// -1500 W for one hour = 1500 Wh
				.next(new TestCase() //
						.timeleap(this.clock, 1, ChronoUnit.HOURS) //
						.output(ACTIVE_PRODUCTION_ENERGY, 0L) //
						.output(ACTIVE_CONSUMPTION_ENERGY, 1500L));
	}

	@Test
	public void fawaGridMeterReadsBuyAndSellWithoutPower() throws Exception {
		// The FäWa gateway exposes energy counters only: Energiebezug (buy) at 5802
		// and Energieabgabe (sell) at 5800, both behind its 12288 address offset.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setType(GRID) //
				.setPowerSource(PowerSource.NOT_AVAILABLE) //
				.setEnergyAddress(18090) //
				.setEnergyEncoding(UINT32) //
				.setEnergyWordOrder(LOW_WORD_FIRST) //
				.setEnergyScaleFactor(HUNDRED) //
				.setReverseEnergyEnabled(true) //
				.setReverseEnergyAddress(18088) //
				.setReverseEnergyEncoding(UINT32) //
				.setReverseEnergyWordOrder(LOW_WORD_FIRST) //
				.setReverseEnergyScaleFactor(HUNDRED) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, null) //
						// buy 1147304.8 kWh
						.output(ACTIVE_PRODUCTION_ENERGY, 1_147_304_800L) //
						// sell 152564.2 kWh
						.output(ACTIVE_CONSUMPTION_ENERGY, 152_564_200L));
	}

	@Test
	public void splitTasksReadBothValues() throws Exception {
		// Power at 0 and energy at 24 are 26 registers apart: two Modbus transactions.
		this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(0) //
				.setEnergyAddress(24) //
				.setEnergyWordOrder(LOW_WORD_FIRST) //
				.build()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 1000) //
						.output(ACTIVE_PRODUCTION_ENERGY, 98_765L));
	}

	@Test
	public void activateRejectsAnInvalidConfiguration() throws Exception {
		var thrown = assertThrows(Exception.class, () -> this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerAddress(100) //
				.setPowerEncoding(INT32) //
				.setEnergyAddress(101) //
				.setEnergyEncoding(INT32) //
				.build()));
		rootCause(thrown);
	}

	@Test
	public void activateRejectsIntegrationWithoutPower() throws Exception {
		var thrown = assertThrows(Exception.class, () -> this.test.activate(MyConfig.create() //
				.setId("meter0") //
				.setModbusId("modbus0") //
				.setPowerSource(PowerSource.NOT_AVAILABLE) //
				.setEnergySource(EnergySource.INTEGRATE_FROM_POWER) //
				.build()));
		var message = rootCause(thrown).getMessage();
		assertTrue(message, message.contains("requires Energy source"));
	}

	/**
	 * Finds the {@link OpenemsException} behind an activation failure, which
	 * ComponentTest wraps.
	 *
	 * @param thrown the exception thrown by activation
	 * @return the first {@link OpenemsException} in the cause chain
	 */
	private static OpenemsException rootCause(Throwable thrown) {
		var cause = thrown;
		while (cause != null && !(cause instanceof OpenemsException)) {
			cause = cause.getCause();
		}
		assertTrue("Expected an OpenemsException, got [" + thrown + "]", cause instanceof OpenemsException);
		return (OpenemsException) cause;
	}
}
