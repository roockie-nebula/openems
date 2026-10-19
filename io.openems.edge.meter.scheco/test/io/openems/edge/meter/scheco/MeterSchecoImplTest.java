package io.openems.edge.meter.scheco;

import static io.openems.common.types.MeterType.CONSUMPTION_METERED;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.api.ElectricityMeter;

public class MeterSchecoImplTest {

	/**
	 * Builds a {@link ComponentTest} with the gateway registers pre-filled.
	 *
	 * <p>
	 * Values are stored Higher-Word first (WordOrder.MSWLSW), the OpenEMS default.
	 *
	 * <ul>
	 * <li>MAIN_CONTROL (energy 9202, power 9204): energy raw 100000 (1/10 kWh),
	 * power raw 123456 W.
	 * <li>KITCHEN (energy 9210, power 9212): energy raw 70000 (1/10 kWh), power raw
	 * 80000 W.
	 * <li>Custom address 9250 (power 9252): energy raw 50000 (1/10 kWh), power raw
	 * 60000 W.
	 * </ul>
	 *
	 * @return the pre-configured {@link ComponentTest}
	 * @throws Exception on error
	 */
	private static ComponentTest buildTest() throws Exception {
		return new ComponentTest(new MeterSchecoImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0") //
						// MAIN_CONTROL: energy 100000 = 0x000186A0, power 123456 = 0x0001E240
						.withRegisters(9202, 0x0001, 0x86A0, 0x0001, 0xE240) //
						// KITCHEN: energy 70000 = 0x00011170, power 80000 = 0x00013880
						.withRegisters(9210, 0x0001, 0x1170, 0x0001, 0x3880) //
						// custom: energy 50000 = 0x0000C350, power 60000 = 0x0000EA60
						.withRegisters(9250, 0x0000, 0xC350, 0x0000, 0xEA60));
	}

	@Test
	public void testNonInvert() throws Exception {
		buildTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(10) //
						.setType(CONSUMPTION_METERED) //
						.setMeter(SubMeter.MAIN_CONTROL) //
						.setInvert(false) //
						.build()) //
				.next(new TestCase() //
						// 100000 * 100 = 10_000_000 Wh
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 10_000_000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 123456));
	}

	@Test
	public void testInvert() throws Exception {
		buildTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(10) //
						.setType(CONSUMPTION_METERED) //
						.setMeter(SubMeter.MAIN_CONTROL) //
						.setInvert(true) //
						.build()) //
				.next(new TestCase() //
						// energy is not inverted, only the power is negated
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 10_000_000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -123456));
	}

	@Test
	public void testSubMeterRouting() throws Exception {
		buildTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(10) //
						.setType(CONSUMPTION_METERED) //
						.setMeter(SubMeter.KITCHEN) //
						.setInvert(false) //
						.build()) //
				.next(new TestCase() //
						// 70000 * 100 = 7_000_000 Wh
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 7_000_000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 80000));
	}

	@Test
	public void testCustomAddress() throws Exception {
		buildTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(10) //
						.setType(CONSUMPTION_METERED) //
						.setMeter(SubMeter.CUSTOM) //
						.setCustomEnergyAddress(9250) //
						.setInvert(false) //
						.build()) //
				.next(new TestCase() //
						// 50000 * 100 = 5_000_000 Wh
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 5_000_000L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 60000));
	}

	@Test
	public void testCustomAddressOutOfRange() throws Exception {
		var test = buildTest();
		// ComponentTest wraps activation failures; verify the root cause
		var thrown = assertThrows(Exception.class, () -> test //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(10) //
						.setType(CONSUMPTION_METERED) //
						.setMeter(SubMeter.CUSTOM) //
						.setCustomEnergyAddress(65534) //
						.setInvert(false) //
						.build()));
		Throwable cause = thrown.getCause();
		while (cause != null && !(cause instanceof OpenemsException)) {
			cause = cause.getCause();
		}
		assertTrue(cause instanceof OpenemsException);
	}
}
