# Migration: `Meter.Scheco` → `Meter.Modbus`

`io.openems.edge.meter.scheco` is superseded by the configuration-driven
`io.openems.edge.meter.modbus`; see that bundle's `readme.adoc` for what each
property means.

**Both bundles ship.** The Scheco bundle was removed in 922dcdda2 and restored
afterwards, because gateways in the field still run `Meter.Scheco` and cannot
be migrated on short notice. The two components have distinct names
(`Meter.Scheco`, `Meter.Modbus`) and distinct configuration, so an instance of
each can run on the same gateway against the same Modbus bridge. Migrate one
instance at a time; migrate a given sub-meter only once, or its energy is
counted twice.

Configure new installations with `Meter.Modbus`. Each `Meter.Scheco` instance
becomes one `Meter.Modbus` instance.

## Site

Seerestaurant im Schilf, 8610 Uster. Modbus/TCP, `192.168.17.10:502`, slave
address 10. Vendor register list: [`docs/vendor/scheco.pdf`](../vendor/scheco.pdf)
(Kommunikationsliste D18556.999 V1.3).

## Settings shared by all twelve meters

| Property | Value |
|---|---|
| `modbusUnitId` | `10` |
| `priority` | `LOW` |
| `powerRegisterType` | `HOLDING_REGISTERS` |
| `powerEncoding` | `INT32` |
| `powerWordOrder` | `HIGH_WORD_FIRST` |
| `powerScaleFactor` | `ONE` (registers are in W) |
| `powerInvert` | `false` |
| `energySource` | `READ_FROM_REGISTER` |
| `energyRegisterType` | `HOLDING_REGISTERS` |
| `energyEncoding` | `INT32` |
| `energyWordOrder` | `HIGH_WORD_FIRST` |
| `energyScaleFactor` | `HUNDRED` (registers are in 1/10 kWh) |

Each meter occupies four contiguous holding registers — energy first, then
power — so each costs exactly one Modbus transaction.

## Per-meter settings

| Former `SubMeter` | `alias` | `energyAddress` | `powerAddress` |
|---|---|---|---|
| `MAIN_CONTROL` | Elektrozähler Hauptsteuerung | 9202 | 9204 |
| `HEAT_PUMP` | Elektrozähler Wärmepumpe | 9206 | 9208 |
| `KITCHEN` | Elektrozähler Küche | 9210 | 9212 |
| `HOUSE_SUPPLY` | Elektrozähler Zuleitung Hauseinspeisung | 9214 | 9216 |
| `VENTILATION` | Elektrozähler Abgang Lüftungsanlage | 9218 | 9220 |
| `GWK` | Elektrozähler Abgang GWK | 9222 | 9224 |
| `WC` | Elektrozähler öffentliche Toiletten | 9226 | 9228 |
| `HEAT_WZ_HEAT_PUMP` | Wärmezähler Wärmepumpe (WZ 1.1) | 9230 | 9232 |
| `HEAT_WZ_HOT_WATER` | Wärmezähler Warmwasser (WZ 1.2) | 9234 | 9236 |
| `HEAT_WZ_FLOOR_HEATING` | Wärmezähler FBH (WZ 1.3) | 9238 | 9240 |
| `HEAT_WZ_COOLING` | Wärmezähler AWN Gew. Kälte (WZ 2.1) | 9242 | 9244 |
| `HEAT_WZ_KVS` | Wärmezähler KVS (WZ 3.1) | 9246 | 9248 |

A former `CUSTOM` instance maps directly: `customEnergyAddress` →
`energyAddress`, and `customEnergyAddress + 2` → `powerAddress`.

## Note on the Wärmezähler

The last five are thermal meters. They map onto `ActivePower` and
`ActiveConsumptionEnergy` like everything else, so the German `alias` is the
only thing distinguishing thermal from electrical kWh downstream. Keep it.
