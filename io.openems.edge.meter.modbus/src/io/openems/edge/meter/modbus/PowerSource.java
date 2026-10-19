package io.openems.edge.meter.modbus;

import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Where the value of {@link ElectricityMeter.ChannelId#ACTIVE_POWER} comes
 * from.
 */
public enum PowerSource {

	/** Read the active power from a register on the device. */
	READ_FROM_REGISTER,

	/**
	 * No power value; the channel stays undefined. For devices that only expose
	 * cumulative energy counters.
	 */
	NOT_AVAILABLE;
}
