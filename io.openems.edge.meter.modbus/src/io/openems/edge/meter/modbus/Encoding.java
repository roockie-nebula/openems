package io.openems.edge.meter.modbus;

import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.SignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;

/**
 * How a value is encoded in the Modbus registers.
 *
 * <p>
 * Byte order within a register is always big-endian, as mandated by the Modbus
 * specification. Word order is only meaningful for the 32-bit encodings.
 */
public enum Encoding {
	/** 16-bit signed integer. */
	INT16(1),
	/** 16-bit unsigned integer. */
	UINT16(1),
	/** 32-bit signed integer. */
	INT32(2),
	/** 32-bit unsigned integer. */
	UINT32(2),
	/** 32-bit IEEE-754 single-precision float. */
	FLOAT32(2);

	private final int registerCount;

	private Encoding(int registerCount) {
		this.registerCount = registerCount;
	}

	/**
	 * Gets the number of 16-bit registers occupied by this encoding.
	 *
	 * @return the register count
	 */
	public int getRegisterCount() {
		return this.registerCount;
	}

	/**
	 * Creates the {@link ModbusElement} for this encoding.
	 *
	 * @param address   the address of the first register
	 * @param wordOrder the {@link WordOrder}; ignored for 16-bit encodings
	 * @return the {@link ModbusElement}
	 */
	public ModbusElement createElement(int address, WordOrder wordOrder) {
		return switch (this) {
		case INT16 -> new SignedWordElement(address);
		case UINT16 -> new UnsignedWordElement(address);
		case INT32 -> new SignedDoublewordElement(address).wordOrder(wordOrder);
		case UINT32 -> new UnsignedDoublewordElement(address).wordOrder(wordOrder);
		case FLOAT32 -> new FloatDoublewordElement(address).wordOrder(wordOrder);
		};
	}
}
