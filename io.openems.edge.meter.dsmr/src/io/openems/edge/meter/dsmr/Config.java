package io.openems.edge.meter.dsmr;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.types.MeterType;

@ObjectClassDefinition(//
		name = "Meter DSMR", //
		description = "Reads a Dutch Smart Meter (DSMR 5.0) via its P1 serial port.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Meter-Type", description = "Grid, Production or Consumption meter")
	MeterType type() default MeterType.GRID;

	@AttributeDefinition(name = "Serial port", description = "Path to the P1 serial adapter, e.g. /dev/ttyUSB0")
	String port() default "/dev/ttyUSB0";

	@AttributeDefinition(name = "DSMR version", description = "Telegram format version")
	DsmrVersion dsmrVersion() default DsmrVersion.V5_0;

	String webconsole_configurationFactory_nameHint() default "Meter DSMR [{id}]";
}
