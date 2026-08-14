package io.openems.edge.meter.modbus;

/**
 * Which half of a 32-bit value sits at the lower register address.
 *
 * <p>
 * A 32-bit value does not fit in one 16-bit Modbus register, so it occupies two.
 * This says which half comes first, counting upwards from the configured
 * address.
 *
 * <p>
 * Byte order <em>within</em> a register is always big-endian, as mandated by the
 * Modbus specification, and is not configurable. A vendor document stating
 * "Endianess: BigEndian" is therefore saying nothing about word order - the WP
 * Friedhofallee controller says exactly that and still needs
 * {@link #LOW_WORD_FIRST}.
 */
public enum WordOrder {

	/**
	 * Bits 16-31 at the lower address. Vendor documents describe this as
	 * "HW"/"Higher-Word" first, and the Modbus specification treats it as the
	 * default.
	 */
	HIGH_WORD_FIRST(io.openems.edge.bridge.modbus.api.element.WordOrder.MSWLSW),

	/**
	 * Bits 0-15 at the lower address. Vendor documents describe this as
	 * "LW"/"Lower-Word" or a "_low" register listed before its "_high" counterpart.
	 */
	LOW_WORD_FIRST(io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW);

	private final io.openems.edge.bridge.modbus.api.element.WordOrder wordOrder;

	private WordOrder(io.openems.edge.bridge.modbus.api.element.WordOrder wordOrder) {
		this.wordOrder = wordOrder;
	}

	/**
	 * Gets the equivalent word order of the Modbus bridge.
	 *
	 * @return the bridge's {@code WordOrder}
	 */
	public io.openems.edge.bridge.modbus.api.element.WordOrder toBridge() {
		return this.wordOrder;
	}
}
