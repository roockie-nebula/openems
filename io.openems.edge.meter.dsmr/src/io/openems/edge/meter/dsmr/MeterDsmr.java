package io.openems.edge.meter.dsmr;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterDsmr extends ElectricityMeter, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/** Communication with the P1 port failed. */
		COMMUNICATION_FAILED(Doc.of(Level.FAULT)),
		/** A telegram failed CRC validation. */
		CRC_ERROR(Doc.of(Level.WARNING));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#COMMUNICATION_FAILED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getCommunicationFailedChannel() {
		return this.channel(ChannelId.COMMUNICATION_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#COMMUNICATION_FAILED}.
	 *
	 * @param value the next value
	 */
	public default void _setCommunicationFailed(boolean value) {
		this.getCommunicationFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CRC_ERROR}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getCrcErrorChannel() {
		return this.channel(ChannelId.CRC_ERROR);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CRC_ERROR}.
	 *
	 * @param value the next value
	 */
	public default void _setCrcError(boolean value) {
		this.getCrcErrorChannel().setNextValue(value);
	}
}
