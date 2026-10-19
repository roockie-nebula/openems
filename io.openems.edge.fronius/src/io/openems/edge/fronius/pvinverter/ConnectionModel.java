package io.openems.edge.fronius.pvinverter;

import static io.openems.common.channel.AccessMode.READ_ONLY;
import static io.openems.edge.bridge.modbus.sunspec.Point.EnumPoint.Type.ENUM16;
import static io.openems.edge.bridge.modbus.sunspec.Point.ValuePoint.Type.UINT16;

import io.openems.common.channel.Unit;
import io.openems.edge.bridge.modbus.sunspec.DefaultSunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.DefaultSunSpecModel.S123_Conn;
import io.openems.edge.bridge.modbus.sunspec.FilteredSunSpecModel;
import io.openems.edge.bridge.modbus.sunspec.Point;
import io.openems.edge.bridge.modbus.sunspec.Point.EnumPoint;
import io.openems.edge.bridge.modbus.sunspec.Point.ValuePoint;
import io.openems.edge.bridge.modbus.sunspec.SunSpecPoint;

/** Only the first three model 123 points, with writes restricted to SetConnection. */
enum ConnectionModel implements SunSpecPoint {
	CONN_WIN_TMS(new ValuePoint("S123_CONN_WIN_TMS", "Conn_WinTms", "Connection time window", UINT16, false,
			READ_ONLY, Unit.SECONDS)), //
	CONN_RVRT_TMS(new ValuePoint("S123_CONN_RVRT_TMS", "Conn_RvrtTms", "Connection reversion timeout", UINT16, false,
			READ_ONLY, Unit.SECONDS)), //
	CONN(new EnumPoint("S123_CONN", "Conn", "Connection permission", ENUM16, true, READ_ONLY, S123_Conn.values()));

	static final FilteredSunSpecModel MODEL = new FilteredSunSpecModel(DefaultSunSpecModel.S_123, values());

	private final Point point;

	private ConnectionModel(Point point) {
		this.point = point;
	}

	@Override
	public Point get() {
		return this.point;
	}
}
