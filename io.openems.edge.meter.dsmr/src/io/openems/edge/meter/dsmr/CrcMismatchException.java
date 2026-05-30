package io.openems.edge.meter.dsmr;

public class CrcMismatchException extends DsmrException {

	private static final long serialVersionUID = 1L;

	public CrcMismatchException(String message) {
		super(message);
	}
}
