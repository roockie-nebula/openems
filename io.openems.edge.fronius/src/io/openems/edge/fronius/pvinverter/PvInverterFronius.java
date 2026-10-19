package io.openems.edge.fronius.pvinverter;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.sunspec.DefaultSunSpecModel.S123_Conn;
import io.openems.edge.bridge.modbus.sunspec.pvinverter.SunSpecPvInverter;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public interface PvInverterFronius extends SunSpecPvInverter, ManagedSymmetricPvInverter, ElectricityMeter,
		ModbusComponent, OpenemsComponent, EventHandler, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		SET_CONNECTION(Doc.of(S123_Conn.values()) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.text("Request standby (0) or permit grid-feed operation (1)")), //
		CONNECTION_CONTROL_AVAILABLE(Doc.of(OpenemsType.BOOLEAN) //
				.text("Connection control is supported, writable and communicating")), //
		CONNECTION_COMMAND_REJECTED(Doc.of(Level.WARNING) //
				.text("Connection command rejected: invalid value or control unavailable")) //
		;
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
	 * Gets the explicit connection command channel.
	 *
	 * @return the channel
	 */
	public default EnumWriteChannel getSetConnectionChannel() {
		return this.channel(ChannelId.SET_CONNECTION);
	}
}
