package io.openems.edge.meter.dsmr;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Reads DSMR P1 telegrams from a stream. A telegram starts with a line
 * beginning with '/' and ends with a line beginning with '!'. Lines are
 * re-joined with CRLF to reproduce the on-wire bytes for CRC validation.
 */
public class TelegramReader {

	private final BufferedReader reader;

	public TelegramReader(InputStream in) {
		this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
	}

	/**
	 * Blocks until a full telegram has been read.
	 *
	 * @return the raw telegram text (incl. the CRC line)
	 * @throws EOFException if the stream ends before a complete telegram
	 * @throws IOException  on read error
	 */
	public String readTelegram() throws IOException {
		var sb = new StringBuilder();
		var started = false;
		String line;
		while ((line = this.reader.readLine()) != null) {
			if (line.startsWith("/")) {
				sb.setLength(0);
				started = true;
			}
			if (!started) {
				continue;
			}
			sb.append(line).append("\r\n");
			if (line.startsWith("!")) {
				return sb.toString();
			}
		}
		throw new EOFException("Stream closed before telegram completed");
	}
}
