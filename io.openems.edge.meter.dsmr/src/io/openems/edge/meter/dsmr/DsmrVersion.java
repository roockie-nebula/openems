package io.openems.edge.meter.dsmr;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Supported DSMR P1 telegram versions together with the serial line parameters
 * each version transmits with.
 */
public enum DsmrVersion {

	/** DSMR 5.0: 115200 baud, 8 data bits, 1 stop bit, no parity. */
	V5_0(115200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
	// V4_X (9600 baud, 7E1) reserved for future support

	private final int baudRate;
	private final int dataBits;
	private final int stopBits;
	private final int parity;

	DsmrVersion(int baudRate, int dataBits, int stopBits, int parity) {
		this.baudRate = baudRate;
		this.dataBits = dataBits;
		this.stopBits = stopBits;
		this.parity = parity;
	}

	/**
	 * Applies this version's serial line parameters to the given port.
	 *
	 * @param port the {@link SerialPort} to configure
	 */
	public void configure(SerialPort port) {
		port.setComPortParameters(this.baudRate, this.dataBits, this.stopBits, this.parity);
	}
}
