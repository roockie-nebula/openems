package io.openems.edge.meter.dsmr;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TelegramTest {

	/** Body from '/' through '!' inclusive, CRLF line endings. */
	private static final String BODY = "/ISk5\\2MT382-1000\r\n" //
			+ "\r\n" //
			+ "1-3:0.2.8(50)\r\n" //
			+ "0-0:96.1.1(4B384547303034303436333935353037)\r\n" //
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

	static String withValidCrc(String body) {
		var bytes = body.getBytes(US_ASCII);
		return body + String.format("%04X", Crc16.calculate(bytes, bytes.length)) + "\r\n";
	}

	@Test
	public void parsesObisValues() throws Exception {
		var t = Telegram.parse(withValidCrc(BODY));
		assertEquals(123.456, t.getAsDouble("1-0:1.8.1").get(), 0.0001);
		assertEquals(1.193, t.getAsDouble("1-0:1.7.0").get(), 0.0001);
		assertEquals(230.1, t.getAsDouble("1-0:32.7.0").get(), 0.0001);
		assertEquals("50", t.getAsString("1-3:0.2.8").get());
		assertTrue(t.getAsDouble("1-0:99.99.0").isEmpty()); // missing OBIS
	}

	@Test
	public void rejectsBadCrc() {
		var bytes = BODY.getBytes(US_ASCII);
		var bad = BODY + String.format("%04X", Crc16.calculate(bytes, bytes.length) ^ 0x1) + "\r\n";
		assertThrows(CrcMismatchException.class, () -> Telegram.parse(bad));
	}

	@Test
	public void missingPhasesStayAbsent() throws Exception {
		var t = Telegram.parse(withValidCrc(BODY));
		assertFalse(t.getAsDouble("1-0:52.7.0").isPresent()); // L2 voltage not in body
	}
}
