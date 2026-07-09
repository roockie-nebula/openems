package io.openems.edge.meter.dsmr;

import java.io.EOFException;
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

	// Longer than the ~1s DSMR 5.0 push interval, so a missing telegram is treated
	// as a communication fault rather than a normal idle gap.
	private static final int READ_TIMEOUT_MS = 15_000;
	// On an electrically noisy P1 link, occasional CRC failures are normal: the bad
	// telegram is discarded and the last-good values are kept. CrcError (WARNING) is
	// only raised once NO valid telegram has arrived for this long, i.e. the link is
	// alive but delivering nothing usable. Prevents the state flapping on every
	// corrupted frame while still surfacing a genuinely unusable link.
	private static final long CRC_ERROR_GRACE_MS = 15_000;

	private final Logger log = LoggerFactory.getLogger(MeterDsmrImpl.class);
	private final ReadWorker worker = new ReadWorker();

	private MeterType meterType = MeterType.GRID;
	private DsmrVersion dsmrVersion = DsmrVersion.V5_0;
	private String port;
	private boolean invert;
	private SerialPort serialPort;
	// Monotonic timestamp (ms) of the last CRC-valid telegram, used to debounce
	// CrcError. Only touched from the single worker thread.
	private long lastValidTelegramMs;

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
		this.dsmrVersion = config.dsmrVersion();
		this.port = config.port();
		this.invert = config.invert();
		this.lastValidTelegramMs = monotonicMillis();
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
					var sp = MeterDsmrImpl.this.serialPort;
					MeterDsmrImpl.this.log.info("DSMR [{}] serial port opened ({} baud)",
							MeterDsmrImpl.this.port, sp.getBaudRate());
					this.reader = new TelegramReader(sp.getInputStream());
					// Grant a fresh CrcError grace window after each (re)connect.
					MeterDsmrImpl.this.lastValidTelegramMs = monotonicMillis();
				}
				MeterDsmrImpl.this.applyTelegram(this.reader.readTelegram());
			} catch (EOFException e) {
				// readLine() returned null => native read() returned -1. Per jSerialComm this
				// is a device error/disconnect, NOT an idle timeout. Log the OS errno so we can
				// tell contention (EBUSY/EAGAIN) from a vanished device (EIO/ENXIO/EBADF).
				var sp = MeterDsmrImpl.this.serialPort;
				MeterDsmrImpl.this.log.error("DSMR [{}] read EOF: portOpen={}, errno={}, errLoc={}",
						MeterDsmrImpl.this.port, MeterDsmrImpl.this.isPortOpen(),
						sp != null ? sp.getLastErrorCode() : -1, sp != null ? sp.getLastErrorLocation() : -1);
				MeterDsmrImpl.this.onReadFault();
				throw e;
			} catch (IOException e) {
				// SerialPortTimeoutException lands here (idle >15s, i.e. no data at all).
				MeterDsmrImpl.this.log.error("DSMR [{}] read I/O error: {}", MeterDsmrImpl.this.port, e.toString());
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
		this.dsmrVersion.configure(sp);
		// SEMI_BLOCKING returns as soon as any bytes are available, so telegrams flow
		// at the meter's ~1s cadence; the timeout still surfaces a dead link as a fault.
		sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0);
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
		this.applyTelegram(raw, monotonicMillis());
	}

	/**
	 * Parses a raw telegram at the given monotonic time. Split out from
	 * {@link #applyTelegram(String)} so the CrcError debounce can be tested with a
	 * controlled clock.
	 *
	 * @param raw   the raw telegram text
	 * @param nowMs the current monotonic timestamp in milliseconds
	 */
	void applyTelegram(String raw, long nowMs) {
		Telegram t;
		try {
			t = Telegram.parse(raw);
		} catch (CrcMismatchException e) {
			// Discard the corrupted telegram and keep the last-good values. Only raise
			// CrcError once the link has gone silent-but-noisy past the grace period.
			if (nowMs - this.lastValidTelegramMs >= CRC_ERROR_GRACE_MS) {
				this._setCrcError(true);
			}
			this.log.debug("DSMR [{}] discarding telegram: {}", this.port, e.getMessage());
			return;
		} catch (DsmrException e) {
			this.log.debug("Malformed telegram: {}", e.getMessage());
			return;
		}

		this._setActivePower(this.applyInvert(toWatt(t, "1-0:1.7.0", "1-0:2.7.0")));
		this._setActivePowerL1(this.applyInvert(toWatt(t, "1-0:21.7.0", "1-0:22.7.0")));
		this._setActivePowerL2(this.applyInvert(toWatt(t, "1-0:41.7.0", "1-0:42.7.0")));
		this._setActivePowerL3(this.applyInvert(toWatt(t, "1-0:61.7.0", "1-0:62.7.0")));

		this._setVoltageL1(toMilli(t, "1-0:32.7.0"));
		this._setVoltageL2(toMilli(t, "1-0:52.7.0"));
		this._setVoltageL3(toMilli(t, "1-0:72.7.0"));

		this._setCurrentL1(toMilli(t, "1-0:31.7.0"));
		this._setCurrentL2(toMilli(t, "1-0:51.7.0"));
		this._setCurrentL3(toMilli(t, "1-0:71.7.0"));

		// GRID convention: ProductionEnergy = integral of positive (buy-from-grid)
		// power, so the import register 1.8.x maps to ProductionEnergy and the export
		// register 2.8.x maps to ConsumptionEnergy. With 'invert' the mapping is
		// swapped.
		var importEnergy = toWattHours(t, "1-0:1.8.1", "1-0:1.8.2");
		var exportEnergy = toWattHours(t, "1-0:2.8.1", "1-0:2.8.2");
		this._setActiveProductionEnergy(this.invert ? exportEnergy : importEnergy);
		this._setActiveConsumptionEnergy(this.invert ? importEnergy : exportEnergy);

		this._setCrcError(false);
		this._setCommunicationFailed(false);
		this.lastValidTelegramMs = nowMs;
	}

	private Integer applyInvert(Integer value) {
		if (value == null) {
			return null;
		}
		return this.invert ? -value : value;
	}

	/**
	 * A monotonic millisecond clock, immune to wall-clock/NTP steps.
	 *
	 * @return the current value in milliseconds
	 */
	private static long monotonicMillis() {
		return System.nanoTime() / 1_000_000L;
	}

	// (plus - minus) kW -> W; null if neither present.
	private static Integer toWatt(Telegram t, String plusObis, String minusObis) {
		var plus = t.getAsDouble(plusObis);
		var minus = t.getAsDouble(minusObis);
		if (plus.isEmpty() && minus.isEmpty()) {
			return null;
		}
		var value = plus.orElse(0.0) - minus.orElse(0.0);
		return (int) Math.round(value * 1000.0);
	}

	// V -> mV or A -> mA (x1000); null if absent.
	private static Integer toMilli(Telegram t, String obis) {
		var v = t.getAsDouble(obis);
		return v.map(d -> (int) Math.round(d * 1000.0)).orElse(null);
	}

	// (a + b) kWh -> Wh; null if both absent.
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
