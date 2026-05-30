package io.openems.edge.meter.dsmr;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A parsed DSMR P1 telegram: OBIS reference -&gt; first parenthesised value.
 */
public class Telegram {

	// Matches e.g. "1-0:1.8.1(000123.456*kWh)" -> group(1)=OBIS, group(2)=content
	private static final Pattern LINE = Pattern.compile("^([0-9]+-[0-9]+:[0-9]+\\.[0-9]+\\.[0-9]+)\\((.*)\\).*$");

	private final Map<String, String> values;

	private Telegram(Map<String, String> values) {
		this.values = values;
	}

	/**
	 * Parses and CRC-validates a raw telegram (from '/' to the CRC line).
	 *
	 * @param raw the raw telegram text
	 * @return the parsed {@link Telegram}
	 * @throws CrcMismatchException if the CRC does not match
	 * @throws DsmrException        if the telegram is malformed
	 */
	public static Telegram parse(String raw) throws DsmrException {
		var start = raw.indexOf('/');
		var bang = raw.indexOf('!', start);
		if (start < 0 || bang < 0) {
			throw new DsmrException("No telegram delimiters found");
		}
		// CRC over '/' .. '!' inclusive
		var body = raw.substring(start, bang + 1);
		if (raw.length() >= bang + 5) {
			var crcHex = raw.substring(bang + 1, bang + 5).trim();
			if (crcHex.length() == 4) {
				var bytes = body.getBytes(StandardCharsets.US_ASCII);
				var expected = Crc16.calculate(bytes, bytes.length);
				int actual;
				try {
					actual = Integer.parseInt(crcHex, 16);
				} catch (NumberFormatException e) {
					throw new DsmrException("Invalid CRC field: " + crcHex);
				}
				if (expected != actual) {
					throw new CrcMismatchException(
							String.format("CRC mismatch: expected %04X got %04X", expected, actual));
				}
			}
		}
		var values = new HashMap<String, String>();
		for (var line : body.split("\r?\n")) {
			var m = LINE.matcher(line.trim());
			if (m.matches()) {
				values.putIfAbsent(m.group(1), m.group(2));
			}
		}
		return new Telegram(values);
	}

	/**
	 * Gets the raw content of the OBIS value, e.g. "000123.456*kWh".
	 *
	 * @param obis the OBIS reference
	 * @return the raw value, or empty if absent
	 */
	public Optional<String> getAsString(String obis) {
		return Optional.ofNullable(this.values.get(obis));
	}

	/**
	 * Gets the numeric part of the OBIS value (unit suffix after '*' stripped).
	 *
	 * @param obis the OBIS reference
	 * @return the numeric value, or empty if absent or unparseable
	 */
	public Optional<Double> getAsDouble(String obis) {
		var raw = this.values.get(obis);
		if (raw == null) {
			return Optional.empty();
		}
		var num = raw;
		var star = num.indexOf('*');
		if (star >= 0) {
			num = num.substring(0, star);
		}
		try {
			return Optional.of(Double.parseDouble(num.trim()));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
