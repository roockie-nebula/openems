package io.openems.edge.meter.huawei.sdongle;

import org.junit.Test;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;

public class MeterHuaweiSDongleImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new MeterHuaweiSDongleImpl()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}
}
