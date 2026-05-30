package io.openems.edge.meter.dsmr;

import static org.junit.Assert.assertEquals;

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

	@Test
	public void mapsTelegramToChannels() throws Exception {
		var sut = new MeterDsmrImpl();
		new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setEnabled(false) // do not open a real serial port
						.setType(MeterType.GRID) //
						.setPort("/dev/null") //
						.build());

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
}
