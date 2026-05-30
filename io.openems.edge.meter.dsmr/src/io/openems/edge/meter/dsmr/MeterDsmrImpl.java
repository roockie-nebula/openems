package io.openems.edge.meter.dsmr;

import java.io.IOException;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

import io.openems.common.types.MeterType;
import io.openems.common.worker.AbstractImmediateWorker;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.DSMR", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class MeterDsmrImpl extends AbstractOpenemsComponent
		implements MeterDsmr, ElectricityMeter, OpenemsComponent {

	private static final int BAUD_RATE = 115200;
	// Longer than the ~1s DSMR 5.0 push interval, so a missing telegram is treated
	// as a communication fault rather than a normal idle gap.
	private static final int READ_TIMEOUT_MS = 15_000;

	private final Logger log = LoggerFactory.getLogger(MeterDsmrImpl.class);
	private final ReadWorker worker = new ReadWorker();

	private MeterType meterType = MeterType.GRID;
	private String port;
	private SerialPort serialPort;

	public MeterDsmrImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterDsmr.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.meterType = config.type();
		this.port = config.port();
		if (config.enabled()) {
			this.worker.activate(config.id());
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.worker.deactivate();
		this.closePort();
		super.deactivate();
	}

	/**
	 * Reads telegrams from the serial port and applies them. On I/O error it flags
	 * {@link MeterDsmr.ChannelId#COMMUNICATION_FAILED}, nulls the live channels and
	 * rethrows so the worker backs off before the next reopen attempt.
	 */
	private class ReadWorker extends AbstractImmediateWorker {

		private TelegramReader reader;

		@Override
		protected void forever() throws Throwable {
			try {
				if (!MeterDsmrImpl.this.isPortOpen()) {
					MeterDsmrImpl.this.openPort();
					this.reader = new TelegramReader(MeterDsmrImpl.this.serialPort.getInputStream());
				}
				MeterDsmrImpl.this.applyTelegram(this.reader.readTelegram());
			} catch (IOException e) {
				MeterDsmrImpl.this.onReadFault();
				throw e;
			}
		}
	}

	private boolean isPortOpen() {
		return this.serialPort != null && this.serialPort.isOpen();
	}

	private void openPort() throws IOException {
		var sp = SerialPort.getCommPort(this.port);
		sp.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
		sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, READ_TIMEOUT_MS, 0);
		if (!sp.openPort()) {
			throw new IOException("Unable to open serial port [" + this.port + "]");
		}
		this.serialPort = sp;
	}

	private void closePort() {
		if (this.serialPort != null) {
			this.serialPort.closePort();
			this.serialPort = null;
		}
	}

	private void onReadFault() {
		this._setCommunicationFailed(true);
		this._setActivePower(null);
		this._setActivePowerL1(null);
		this._setActivePowerL2(null);
		this._setActivePowerL3(null);
		this._setVoltageL1(null);
		this._setVoltageL2(null);
		this._setVoltageL3(null);
		this._setCurrentL1(null);
		this._setCurrentL2(null);
		this._setCurrentL3(null);
		this.closePort();
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	/**
	 * Parses a raw telegram and updates the channels. CRC failures and read faults
	 * are reflected in the state channels; this method never throws.
	 *
	 * @param raw the raw telegram text
	 */
	protected void applyTelegram(String raw) {
		Telegram t;
		try {
			t = Telegram.parse(raw);
		} catch (CrcMismatchException e) {
			this._setCrcError(true);
			return;
		} catch (DsmrException e) {
			this.log.debug("Malformed telegram: {}", e.getMessage());
			return;
		}

		this._setActivePower(toWatt(t, "1-0:1.7.0", "1-0:2.7.0"));
		this._setActivePowerL1(toWatt(t, "1-0:21.7.0", "1-0:22.7.0"));
		this._setActivePowerL2(toWatt(t, "1-0:41.7.0", "1-0:42.7.0"));
		this._setActivePowerL3(toWatt(t, "1-0:61.7.0", "1-0:62.7.0"));

		this._setVoltageL1(toMilli(t, "1-0:32.7.0"));
		this._setVoltageL2(toMilli(t, "1-0:52.7.0"));
		this._setVoltageL3(toMilli(t, "1-0:72.7.0"));

		this._setCurrentL1(toMilli(t, "1-0:31.7.0"));
		this._setCurrentL2(toMilli(t, "1-0:51.7.0"));
		this._setCurrentL3(toMilli(t, "1-0:71.7.0"));

		// GRID convention: ProductionEnergy = integral of positive (buy-from-grid)
		// power, so the import register 1.8.x maps to ProductionEnergy and the export
		// register 2.8.x maps to ConsumptionEnergy.
		this._setActiveProductionEnergy(toWattHours(t, "1-0:1.8.1", "1-0:1.8.2"));
		this._setActiveConsumptionEnergy(toWattHours(t, "1-0:2.8.1", "1-0:2.8.2"));

		this._setCrcError(false);
		this._setCommunicationFailed(false);
	}

	/** (plus - minus) kW -&gt; W; null if neither present. */
	private static Integer toWatt(Telegram t, String plusObis, String minusObis) {
		var plus = t.getAsDouble(plusObis);
		var minus = t.getAsDouble(minusObis);
		if (plus.isEmpty() && minus.isEmpty()) {
			return null;
		}
		var value = plus.orElse(0.0) - minus.orElse(0.0);
		return (int) Math.round(value * 1000.0);
	}

	/** V-&gt;mV or A-&gt;mA (x1000); null if absent. */
	private static Integer toMilli(Telegram t, String obis) {
		var v = t.getAsDouble(obis);
		return v.map(d -> (int) Math.round(d * 1000.0)).orElse(null);
	}

	/** (a + b) kWh -&gt; Wh; null if both absent. */
	private static Long toWattHours(Telegram t, String obisA, String obisB) {
		var a = t.getAsDouble(obisA);
		var b = t.getAsDouble(obisB);
		if (a.isEmpty() && b.isEmpty()) {
			return null;
		}
		return Math.round((a.orElse(0.0) + b.orElse(0.0)) * 1000.0);
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}
}
