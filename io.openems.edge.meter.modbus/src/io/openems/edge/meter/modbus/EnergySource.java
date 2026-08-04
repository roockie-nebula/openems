package io.openems.edge.meter.modbus;

import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Where the value of {@link ElectricityMeter.ChannelId#ACTIVE_CONSUMPTION_ENERGY}
 * comes from.
 *
 * <p>
 * The three modes are mutually exclusive by construction. Reading a cumulative
 * energy register <em>and</em> integrating power into the same channel would
 * double-count, so the choice is a single enum rather than an independent flag.
 */
public enum EnergySource {
	/** Read a cumulative energy register from the device. */
	REGISTER,
	/** Integrate the active power over time. For devices without an energy register. */
	FROM_POWER,
	/** No energy value; the channel stays undefined. */
	NONE;
}
