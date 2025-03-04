package io.openems.edge.meter.huawei.sdongle;

import org.junit.Test;

import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyConfigurationAdmin;

public class MyModbusDeviceTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new MeterHuaweiSDongleImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.activate(MyConfig.create() //
						.setId("component0") //
						.setModbusId("modbus0") //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}

}
