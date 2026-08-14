package io.openems.edge.meter.modbus;

import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.taskmanager.Priority;

/**
 * The kind of Modbus register a value is read from.
 *
 * <p>
 * Named after the register kind rather than the function code, because that is
 * the word vendor documentation uses: the WP Friedhofallee register list is
 * literally a column of {@code HoldingRegister} and {@code InputRegister} rows.
 *
 * <p>
 * Coils and discrete inputs are intentionally absent: this bundle reads
 * measurements only, and a measurement is never a single bit.
 */
public enum RegisterType {

	/** Read/write registers, Modbus function code 3. */
	HOLDING_REGISTERS,

	/** Read-only registers, Modbus function code 4. */
	INPUT_REGISTERS;

	/**
	 * Creates the read {@link Task} for this register type.
	 *
	 * @param startAddress the address of the first register to read
	 * @param priority     the {@link Priority} of the task
	 * @param elements     the {@link ModbusElement}s, gapless and ascending
	 * @return the {@link Task}
	 */
	public Task createTask(int startAddress, Priority priority, ModbusElement... elements) {
		return switch (this) {
		case HOLDING_REGISTERS -> new FC3ReadRegistersTask(startAddress, priority, elements);
		case INPUT_REGISTERS -> new FC4ReadInputRegistersTask(startAddress, priority, elements);
		};
	}
}
