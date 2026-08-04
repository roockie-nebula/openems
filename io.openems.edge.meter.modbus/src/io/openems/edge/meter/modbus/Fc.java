package io.openems.edge.meter.modbus;

import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.taskmanager.Priority;

/**
 * The Modbus read function codes supported by this bundle.
 *
 * <p>
 * Coils (FC1) and discrete inputs (FC2) are intentionally absent: this bundle
 * reads measurements only, and a measurement is never a single bit.
 */
public enum Fc {
	/** Read Holding Registers. */
	FC3,
	/** Read Input Registers. */
	FC4;

	/**
	 * Creates the read {@link Task} for this function code.
	 *
	 * @param startAddress the address of the first register to read
	 * @param priority     the {@link Priority} of the task
	 * @param elements     the {@link ModbusElement}s, gapless and ascending
	 * @return the {@link Task}
	 */
	public Task createTask(int startAddress, Priority priority, ModbusElement... elements) {
		return switch (this) {
		case FC3 -> new FC3ReadRegistersTask(startAddress, priority, elements);
		case FC4 -> new FC4ReadInputRegistersTask(startAddress, priority, elements);
		};
	}
}
