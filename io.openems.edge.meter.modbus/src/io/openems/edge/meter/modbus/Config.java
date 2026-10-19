package io.openems.edge.meter.modbus;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.types.MeterType;
import io.openems.edge.common.taskmanager.Priority;

@ObjectClassDefinition(//
		name = "Generic Modbus Meter", //
		description = "Reads an active power and cumulative active energy values from any Modbus device.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID. Use the label from the vendor documentation, and mark thermal meters (Wärmezähler) as such.")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Meter-Type", description = "Grid, Production, Consumption. Defaults to Consumption (metered).")
	MeterType type() default MeterType.CONSUMPTION_METERED;

	@AttributeDefinition(name = "Modbus-ID", description = "Id of the modbus bridge")
	String modbus_id() default "modbus0";

	@AttributeDefinition(name = "Modbus Unit-ID", description = "The Unit-ID of the Modbus device. Some devices ignore it and answer on any Unit-ID.")
	int modbusUnitId() default 1;

	@AttributeDefinition(name = "Priority", description = "HIGH reads this meter on every cycle. LOW shares one read per cycle with every other LOW task on the bridge, which is enough for energy monitoring and keeps the bus quiet.")
	Priority priority() default Priority.LOW;

	@AttributeDefinition(name = "Power: source", description = "READ_FROM_REGISTER reads the active power from a register. NOT_AVAILABLE leaves the power channel undefined, for devices that only expose energy counters; Energy source must then be READ_FROM_REGISTER.")
	PowerSource powerSource() default PowerSource.READ_FROM_REGISTER;

	@AttributeDefinition(name = "Power: register type", description = "The kind of register the active power is read from, as named in the vendor documentation. Only used when Power source is READ_FROM_REGISTER.")
	RegisterType powerRegisterType() default RegisterType.HOLDING_REGISTERS;

	@AttributeDefinition(name = "Power: register address", description = "Address of the first register of the active power value (0-65535). Only used when Power source is READ_FROM_REGISTER.")
	int powerAddress() default 0;

	@AttributeDefinition(name = "Power: encoding", description = "How the active power value is encoded. Only used when Power source is READ_FROM_REGISTER.")
	Encoding powerEncoding() default Encoding.INT32;

	@AttributeDefinition(name = "Power: word order", description = "Which half of a 32-bit value sits at the register address above. Only used for 32-bit encodings. HIGH_WORD_FIRST is the Modbus default; pick LOW_WORD_FIRST when the documentation lists a '_low' register before its '_high' counterpart. Only used when Power source is READ_FROM_REGISTER.")
	WordOrder powerWordOrder() default WordOrder.HIGH_WORD_FIRST;

	@AttributeDefinition(name = "Power: scale factor", description = "What the register value is multiplied by to reach Watt. A register in W uses ONE, in kW uses THOUSAND. Only used when Power source is READ_FROM_REGISTER.")
	ScaleFactor powerScaleFactor() default ScaleFactor.ONE;

	@AttributeDefinition(name = "Power: invert", description = "Negates the active power. Corrects a reversed measurement convention. Does not affect energy registers; with Energy source INTEGRATE_FROM_POWER, the integrated energy follows the inverted power. Only used when Power source is READ_FROM_REGISTER.")
	boolean powerInvert() default false;

	@AttributeDefinition(name = "Energy: source", description = "READ_FROM_REGISTER reads cumulative energy registers. INTEGRATE_FROM_POWER integrates the active power, for devices without one: positive power into ActiveProductionEnergy, negative power into ActiveConsumptionEnergy. NOT_AVAILABLE leaves the energy channels undefined.")
	EnergySource energySource() default EnergySource.READ_FROM_REGISTER;

	@AttributeDefinition(name = "Energy: register type", description = "Only used when Energy source is READ_FROM_REGISTER")
	RegisterType energyRegisterType() default RegisterType.HOLDING_REGISTERS;

	@AttributeDefinition(name = "Energy: register address", description = "Address of the first register of the counter that grows while the active power is positive: consumption for a consumption meter, production for a production meter, buy-from-grid for a grid meter. Read into ActiveProductionEnergy, as the OpenEMS meter convention requires (0-65535). Only used when Energy source is READ_FROM_REGISTER.")
	int energyAddress() default 0;

	@AttributeDefinition(name = "Energy: encoding", description = "Only used when Energy source is READ_FROM_REGISTER")
	Encoding energyEncoding() default Encoding.INT32;

	@AttributeDefinition(name = "Energy: word order", description = "Which half of a 32-bit value sits at the register address above. May differ from the power word order; the WP Friedhofallee controller mixes both. Only used when Energy source is READ_FROM_REGISTER.")
	WordOrder energyWordOrder() default WordOrder.HIGH_WORD_FIRST;

	@AttributeDefinition(name = "Energy: scale factor", description = "What the register value is multiplied by to reach Watt-hours. A register in Wh uses ONE, in 1/10 kWh uses HUNDRED, in kWh uses THOUSAND. Only used when Energy source is READ_FROM_REGISTER.")
	ScaleFactor energyScaleFactor() default ScaleFactor.ONE;

	@AttributeDefinition(name = "Reverse energy: enabled", description = "Also reads a second counter that grows while the active power is negative, e.g. sell-to-grid for a grid meter. Read into ActiveConsumptionEnergy. Only used when Energy source is READ_FROM_REGISTER.")
	boolean reverseEnergyEnabled() default false;

	@AttributeDefinition(name = "Reverse energy: register type", description = "Only used when Reverse energy is enabled")
	RegisterType reverseEnergyRegisterType() default RegisterType.HOLDING_REGISTERS;

	@AttributeDefinition(name = "Reverse energy: register address", description = "Address of the first register of the reverse energy counter (0-65535). Only used when Reverse energy is enabled.")
	int reverseEnergyAddress() default 0;

	@AttributeDefinition(name = "Reverse energy: encoding", description = "Only used when Reverse energy is enabled")
	Encoding reverseEnergyEncoding() default Encoding.INT32;

	@AttributeDefinition(name = "Reverse energy: word order", description = "Which half of a 32-bit value sits at the register address above. Only used when Reverse energy is enabled.")
	WordOrder reverseEnergyWordOrder() default WordOrder.HIGH_WORD_FIRST;

	@AttributeDefinition(name = "Reverse energy: scale factor", description = "What the register value is multiplied by to reach Watt-hours. Only used when Reverse energy is enabled.")
	ScaleFactor reverseEnergyScaleFactor() default ScaleFactor.ONE;

	@AttributeDefinition(name = "Modbus target filter", description = "This is auto-generated by 'Modbus-ID'")
	String Modbus_target() default "(enabled=true)";

	String webconsole_configurationFactory_nameHint() default "Generic Modbus Meter [{id}]";

}
