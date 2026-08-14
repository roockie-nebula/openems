package io.openems.edge.meter.modbus;

/**
 * The factor the raw register value is multiplied by to reach the unit of the
 * Channel - Watt for power, Watt-hours for energy.
 *
 * <p>
 * Chosen from the unit the vendor documents, not calculated: a register in kWh
 * needs {@link #THOUSAND} to become Wh, one in 1/10 kWh needs {@link #HUNDRED},
 * and one already in the target unit needs {@link #ONE}.
 */
public enum ScaleFactor {

	/** Multiply by 1/1000. */
	ONE_THOUSANDTH(-3),
	/** Multiply by 1/100. */
	ONE_HUNDREDTH(-2),
	/** Multiply by 1/10. */
	ONE_TENTH(-1),
	/** Leave the value unchanged. */
	ONE(0),
	/** Multiply by 10. */
	TEN(1),
	/** Multiply by 100. Use for a register in 1/10 kWh. */
	HUNDRED(2),
	/** Multiply by 1000. Use for a register in kW or kWh. */
	THOUSAND(3);

	private final int exponent;

	private ScaleFactor(int exponent) {
		this.exponent = exponent;
	}

	/**
	 * Gets the decimal exponent, i.e. the {@code n} in {@code value * 10^n}.
	 *
	 * @return the exponent
	 */
	public int getExponent() {
		return this.exponent;
	}
}
