package io.openems.edge.meter.modbus;

import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Where the values of {@link ElectricityMeter.ChannelId#ACTIVE_PRODUCTION_ENERGY}
 * and {@link ElectricityMeter.ChannelId#ACTIVE_CONSUMPTION_ENERGY} come from.
 *
 * <p>
 * The three modes are mutually exclusive by construction. Reading a cumulative
 * energy register <em>and</em> integrating power into the same channel would
 * double-count, so the choice is a single enum rather than an independent flag.
 */
public enum EnergySource {

	/** Read cumulative energy registers from the device. */
	READ_FROM_REGISTER,

	/**
	 * Integrate the active power over time: positive power into the production
	 * energy, negative power into the consumption energy. For devices without an
	 * energy register.
	 */
	INTEGRATE_FROM_POWER,

	/** No energy value; the channels stay undefined. */
	NOT_AVAILABLE;
}
