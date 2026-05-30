package io.openems.edge.meter.dsmr;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Crc16Test {

	@Test
	public void checkValue() {
		var data = "123456789".getBytes(US_ASCII);
		assertEquals(0xBB3D, Crc16.calculate(data, data.length));
	}
}
