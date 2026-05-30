package io.openems.edge.meter.dsmr;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.EOFException;

import org.junit.Test;

public class TelegramReaderTest {

	@Test
	public void readsTwoTelegramsThenEof() throws Exception {
		var one = "/AAA\r\n\r\n1-0:1.7.0(01.000*kW)\r\n!1234\r\n";
		var two = "/BBB\r\n\r\n1-0:1.7.0(02.000*kW)\r\n!5678\r\n";
		var noise = "garbage-before\r\n"; // must be ignored until '/'
		var in = new ByteArrayInputStream((noise + one + two).getBytes(US_ASCII));
		var reader = new TelegramReader(in);

		var t1 = reader.readTelegram();
		assertTrue(t1.startsWith("/AAA"));
		assertTrue(t1.contains("!1234"));

		var t2 = reader.readTelegram();
		assertTrue(t2.startsWith("/BBB"));

		assertThrows(EOFException.class, reader::readTelegram);
	}
}
