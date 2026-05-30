package io.openems.edge.meter.dsmr;

/**
 * DSMR CRC16 (CRC-16/ARC: poly 0xA001 reflected, init 0x0000), calculated over
 * the bytes from '/' through '!' inclusive.
 */
public final class Crc16 {

	private Crc16() {
	}

	public static int calculate(byte[] data, int length) {
		var crc = 0x0000;
		for (var i = 0; i < length; i++) {
			crc ^= data[i] & 0xFF;
			for (var bit = 0; bit < 8; bit++) {
				if ((crc & 1) != 0) {
					crc = crc >>> 1 ^ 0xA001;
				} else {
					crc = crc >>> 1;
				}
			}
		}
		return crc & 0xFFFF;
	}
}
