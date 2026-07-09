package io.openems.edge.meter.dsmr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.types.MeterType;
import io.openems.edge.common.test.ComponentTest;

public class MeterDsmrImplTest {

	private static final String TELEGRAM_BODY = "/ISk5\\2MT382-1000\r\n" //
			+ "\r\n" //
			+ "1-0:1.8.1(000123.456*kWh)\r\n" //
			+ "1-0:1.8.2(000234.567*kWh)\r\n" //
			+ "1-0:2.8.1(000012.300*kWh)\r\n" //
			+ "1-0:2.8.2(000045.600*kWh)\r\n" //
			+ "1-0:1.7.0(01.193*kW)\r\n" //
			+ "1-0:2.7.0(00.000*kW)\r\n" //
			+ "1-0:32.7.0(230.1*V)\r\n" //
			+ "1-0:31.7.0(005*A)\r\n" //
			+ "1-0:21.7.0(01.193*kW)\r\n" //
			+ "1-0:22.7.0(00.000*kW)\r\n" //
			+ "!";

	private static MeterDsmrImpl activatedMeter() throws Exception {
		var sut = new MeterDsmrImpl();
		new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setEnabled(false) // do not open a real serial port
						.setType(MeterType.GRID) //
						.setPort("/dev/null") //
						.build());
		return sut;
	}

	@Test
	public void mapsTelegramToChannels() throws Exception {
		var sut = activatedMeter();

		sut.applyTelegram(TelegramTest.withValidCrc(TELEGRAM_BODY));

		// ActivePower = (1.7.0 - 2.7.0) * 1000 = (1.193 - 0) kW -> 1193 W
		assertEquals(1193, (int) sut.getActivePowerChannel().getNextValue().get());
		// ActivePowerL1 = (21.7.0 - 22.7.0)*1000
		assertEquals(1193, (int) sut.getActivePowerL1Channel().getNextValue().get());
		// Voltage L1 230.1 V -> 230100 mV
		assertEquals(230100, (int) sut.getVoltageL1Channel().getNextValue().get());
		// Current L1 5 A -> 5000 mA
		assertEquals(5000, (int) sut.getCurrentL1Channel().getNextValue().get());
		// Import 1.8.x -> ProductionEnergy = (123.456 + 234.567) kWh -> 358023 Wh
		assertEquals(358023L, (long) sut.getActiveProductionEnergyChannel().getNextValue().get());
		// Export 2.8.x -> ConsumptionEnergy = (12.3 + 45.6) kWh -> 57900 Wh
		assertEquals(57900L, (long) sut.getActiveConsumptionEnergyChannel().getNextValue().get());
	}

	@Test
	public void crcErrorIsDebouncedUntilGraceElapses() throws Exception {
		var sut = activatedMeter();

		var good = TelegramTest.withValidCrc(TELEGRAM_BODY);
		// Same body, deliberately wrong CRC -> fails validation like a corrupted frame.
		var bad = TelegramTest.withInvalidCrc(TELEGRAM_BODY);

		// A valid telegram clears CrcError and marks the link healthy.
		sut.applyTelegram(good, 0L);
		assertFalse(sut.getCrcErrorChannel().getNextValue().get());

		// Corrupted telegrams within the grace window keep last-good values, no WARNING.
		sut.applyTelegram(bad, 1_000L);
		sut.applyTelegram(bad, 5_000L);
		assertFalse(sut.getCrcErrorChannel().getNextValue().get());
		assertEquals(1193, (int) sut.getActivePowerChannel().getNextValue().get());

		// Sustained corruption past the grace window raises CrcError.
		sut.applyTelegram(bad, 16_000L);
		assertTrue(sut.getCrcErrorChannel().getNextValue().get());

		// A single valid telegram clears it again.
		sut.applyTelegram(good, 17_000L);
		assertFalse(sut.getCrcErrorChannel().getNextValue().get());
	}

	@Test
	public void debugLogShowsActivePowerOnly() throws Exception {
		var sut = activatedMeter();

		sut.applyTelegram(TelegramTest.withValidCrc(TELEGRAM_BODY), 1_000L);

		var debugLog = sut.debugLog();
		assertTrue(debugLog.contains("L:"));
		assertFalse(debugLog.contains("T:"));
	}
}
