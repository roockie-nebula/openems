package io.openems.edge.meter.modbus;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static java.util.Comparator.comparingInt;

import java.util.ArrayList;
import java.util.List;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ElementToChannelScaleFactorConverter;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.WordOrder;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.taskmanager.Priority;

/**
 * Turns a list of {@link Point}s into Modbus read {@link Task}s.
 *
 * <p>
 * Kept separate from the component so that the merge rules and the element
 * construction can be tested without an OSGi container or a Modbus bridge.
 */
public final class ProtocolBuilder {

	/** Highest addressable Modbus register. */
	public static final int MAX_ADDRESS = 65535;

	/**
	 * Two points are read by one Modbus transaction if the registers they span fit
	 * within this many registers. Reading a few unused registers is cheaper than a
	 * second round-trip; reading a large hole is not.
	 */
	public static final int MAX_MERGE_SPAN = 8;

	/**
	 * One value to read: a Modbus register range mapped onto an OpenEMS Channel.
	 *
	 * @param channelId the target Channel
	 * @param fc        the Modbus read function code
	 * @param address   the address of the first register
	 * @param encoding  the {@link Encoding} of the value
	 * @param wordOrder the {@link WordOrder}; ignored for 16-bit encodings
	 * @param converter the {@link ElementToChannelConverter} to apply
	 */
	public record Point(ChannelId channelId, Fc fc, int address, Encoding encoding, WordOrder wordOrder,
			ElementToChannelConverter converter) {

		/**
		 * Gets the number of registers occupied by this Point.
		 *
		 * @return the register count
		 */
		public int length() {
			return this.encoding.getRegisterCount();
		}

		/**
		 * Gets the address of the last register occupied by this Point, inclusive.
		 *
		 * @return the last register address
		 */
		public int endAddress() {
			return this.address + this.length() - 1;
		}
	}

	/**
	 * Registers a {@link ModbusElement} with a Channel.
	 *
	 * <p>
	 * This exists because the mapping helper is a protected method of
	 * {@code AbstractOpenemsModbusComponent}. The component passes its own
	 * {@code m(...)}; a test passes a no-op.
	 */
	@FunctionalInterface
	public interface ElementMapper {

		/**
		 * Maps a {@link ModbusElement} onto a Channel.
		 *
		 * @param channelId the target Channel
		 * @param element   the {@link ModbusElement}
		 * @param converter the {@link ElementToChannelConverter} to apply
		 * @return the element
		 */
		ModbusElement map(ChannelId channelId, ModbusElement element, ElementToChannelConverter converter);
	}

	private ProtocolBuilder() {
	}

	/**
	 * Builds the {@link ElementToChannelConverter} for a configured scale factor and
	 * invert flag.
	 *
	 * <p>
	 * A scale factor of 0 skips the multiply entirely rather than multiplying by
	 * 1.0, which would turn an exact integer into a float round-trip.
	 *
	 * @param scaleFactor the decimal exponent; {@code channel = register * 10^n}
	 * @param invert      whether to negate the value
	 * @return the {@link ElementToChannelConverter}
	 */
	public static ElementToChannelConverter converter(int scaleFactor, boolean invert) {
		if (scaleFactor == 0) {
			// INVERT_IF_TRUE(false) is DIRECT_1_TO_1
			return INVERT_IF_TRUE(invert);
		}
		return chain(new ElementToChannelScaleFactorConverter(scaleFactor), INVERT_IF_TRUE(invert));
	}

	/**
	 * Validates the {@link Point}s.
	 *
	 * <p>
	 * Called from {@code activate()} so that a bad configuration fails visibly at
	 * activation, rather than later when the Modbus bridge binds and
	 * {@code defineModbusProtocol()} runs.
	 *
	 * @param points the {@link Point}s
	 * @throws OpenemsException on an out-of-range or overlapping Point
	 */
	public static void validate(List<Point> points) throws OpenemsException {
		for (var point : points) {
			if (point.address() < 0 || point.address() > MAX_ADDRESS) {
				throw new OpenemsException("Modbus address [" + point.address() + "] for Channel ["
						+ point.channelId().id() + "] must be within 0.." + MAX_ADDRESS);
			}
			if (point.endAddress() > MAX_ADDRESS) {
				throw new OpenemsException("Modbus address [" + point.address() + "] for Channel ["
						+ point.channelId().id() + "] with encoding [" + point.encoding() + "] runs past "
						+ MAX_ADDRESS);
			}
		}
		for (var i = 0; i < points.size(); i++) {
			for (var j = i + 1; j < points.size(); j++) {
				var a = points.get(i);
				var b = points.get(j);
				if (a.fc() == b.fc() && a.address() <= b.endAddress() && b.address() <= a.endAddress()) {
					throw new OpenemsException("Modbus registers for Channels [" + a.channelId().id() + "] and ["
							+ b.channelId().id() + "] overlap");
				}
			}
		}
	}

	/**
	 * Builds the read {@link Task}s for the given {@link Point}s.
	 *
	 * <p>
	 * Points sharing a function code are merged into one Task while they stay
	 * within {@link #MAX_MERGE_SPAN} registers of the Task's first register.
	 * Merging works from the actual addresses, never from an assumed layout: the
	 * two values may appear in either order, and do, across the known devices.
	 *
	 * <p>
	 * Expects {@link #validate(List)} to have passed.
	 *
	 * @param points   the {@link Point}s
	 * @param priority the {@link Priority} carried by every Task
	 * @param mapper   registers each element with its Channel
	 * @return the {@link Task}s
	 */
	public static List<Task> build(List<Point> points, Priority priority, ElementMapper mapper) {
		var tasks = new ArrayList<Task>();
		for (var fc : Fc.values()) {
			var ofThisFc = points.stream() //
					.filter(p -> p.fc() == fc) //
					.sorted(comparingInt(Point::address)) //
					.toList();
			var group = new ArrayList<Point>();
			for (var point : ofThisFc) {
				if (!group.isEmpty() && point.endAddress() - group.get(0).address() + 1 > MAX_MERGE_SPAN) {
					tasks.add(toTask(fc, group, priority, mapper));
					group = new ArrayList<>();
				}
				group.add(point);
			}
			if (!group.isEmpty()) {
				tasks.add(toTask(fc, group, priority, mapper));
			}
		}
		return tasks;
	}

	/**
	 * Builds one {@link Task} from address-ascending, non-overlapping Points.
	 *
	 * <p>
	 * Interior gaps are filled with a {@link DummyRegisterElement}. This is
	 * mandatory, not an optimisation: {@code AbstractTask} rejects an element that
	 * does not begin exactly where the previous one ended.
	 *
	 * @param fc       the Modbus read function code
	 * @param group    the {@link Point}s, ascending by address
	 * @param priority the {@link Priority}
	 * @param mapper   registers each element with its Channel
	 * @return the {@link Task}
	 */
	private static Task toTask(Fc fc, List<Point> group, Priority priority, ElementMapper mapper) {
		var startAddress = group.get(0).address();
		var elements = new ArrayList<ModbusElement>();
		var nextAddress = startAddress;
		for (var point : group) {
			if (point.address() > nextAddress) {
				elements.add(new DummyRegisterElement(nextAddress, point.address() - 1));
			}
			elements.add(mapper.map(point.channelId(), //
					point.encoding().createElement(point.address(), point.wordOrder()), //
					point.converter()));
			nextAddress = point.address() + point.length();
		}
		return fc.createTask(startAddress, priority, elements.toArray(ModbusElement[]::new));
	}
}
