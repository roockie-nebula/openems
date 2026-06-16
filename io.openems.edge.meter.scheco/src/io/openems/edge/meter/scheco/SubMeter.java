package io.openems.edge.meter.scheco;

/**
 * The sub-meters exposed by the Scheco gateway.
 *
 * <p>
 * Each constant stores the Modbus holding-register address of its 32-bit energy
 * value. The matching power value is located two registers later
 * ({@link #getPowerAddress()} = energy address + 2).
 */
public enum SubMeter {
	MAIN_CONTROL(9202, "Hauptsteuerung"), //
	HEAT_PUMP(9206, "Wärmepumpe"), //
	KITCHEN(9210, "Küche"), //
	HOUSE_SUPPLY(9214, "Zuleitung Hauseinspeisung"), //
	VENTILATION(9218, "Abgang Lüftungsanlage"), //
	GWK(9222, "Abgang GWK"), //
	WC(9226, "öffentliche Toiletten"), //
	HEAT_WZ_HEAT_PUMP(9230, "Wärmezähler Wärmepumpe WZ 1.1"), //
	HEAT_WZ_HOT_WATER(9234, "Wärmezähler Warmwasser WZ 1.2"), //
	HEAT_WZ_FLOOR_HEATING(9238, "Wärmezähler FBH WZ 1.3"), //
	HEAT_WZ_COOLING(9242, "Wärmezähler AWN Kälte WZ 2.1"), //
	HEAT_WZ_KVS(9246, "Wärmezähler KVS WZ 3.1");

	private final int energyAddress;
	private final String label;

	private SubMeter(int energyAddress, String label) {
		this.energyAddress = energyAddress;
		this.label = label;
	}

	/**
	 * Gets the Modbus holding-register address of the 32-bit energy value.
	 *
	 * @return the energy register address
	 */
	public int getEnergyAddress() {
		return this.energyAddress;
	}

	/**
	 * Gets the Modbus holding-register address of the 32-bit power value, which
	 * directly follows the energy value.
	 *
	 * @return the power register address
	 */
	public int getPowerAddress() {
		return this.energyAddress + 2;
	}

	/**
	 * Gets the German label of the sub-meter as used in the vendor documentation.
	 *
	 * @return the label
	 */
	public String getLabel() {
		return this.label;
	}
}
