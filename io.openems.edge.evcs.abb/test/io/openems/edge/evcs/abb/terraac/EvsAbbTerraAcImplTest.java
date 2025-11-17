package io.openems.edge.evcs.abb.terraac;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.api.PhaseRotation;

public class EvsAbbTerraAcImplTest {

	private static final String CID = "meter0";
	private static final String MID = "modbus0";
	private static final MyConfig CONFIG = MyConfig.create() //
			.setId(CID)
			.setModbusId(MID)
			.setModbusUnitId(12)
			.setPhaseRotation(PhaseRotation.L3_L1_L2)
			.setMaxHwPower(6000)
			.setMaxHwPower(11000)
			.build();
	
	private ComponentTest componentTest;

	@Before
	public void before() throws Exception {
		final DummyModbusBridge bridge = new DummyModbusBridge(MID) //
				.withRegisters(EvcsAbbTerraAc.DEVICE_START_ADDRESS | 0x0000, //
						0x5411, // Connector Type / Rated Power 
						0x0400, // Plant ID / Spare
						0x0B19, // Production Week / ProductionYear 
						0x1234, // Serial Number / Unique serial number low high
						0x0102, // Firmware version
						0x0304, //
						0x00ED, // User Settable Max Current
						0x2AD8, // 15543000 mA
						0x0000, // Error Code 
						0x0800, //
						0x0000, // Socket Lock State
						0x0111, //
						0x0000, // Charging State
						0x0900, //
						0x0000, // Charging Current Limit
						0x277F, // 10111 mA
						0x0000, // Charging Current L1 
						0x198F, // 6543 mA 
						0x0000, // Charging Current L2
						0x1920, // 6432 mA
						0x0000, // Charging Current L3 
						0x18B1, // 6321 mA
						0x0000, // Voltage L1
						0x0901, // 230 V
						0x0000, // Voltage L2
						0x0906, // 231 V 
						0x0000, // Voltage L3
						0x08F2, // 229 V
						0x0000, // Active Power
						0x278B, // 10123 W
						0x2B6C, // Energy Delivered
						0x8210, // 728531472 Wh
						0x0017, // Communication timeout 23sec
						0x0000, //
						0x00D7, // Charging current limit set by Modbus
						0x7FF8, // 14123000 mA
						0x000C, // Fallback limit 12 A
						0x0000
						);
		
		this.componentTest = new ComponentTest(new EvcsAbbTerraAcImpl())
				.addReference("cm", new DummyConfigurationAdmin())
				.addReference("setModbus", bridge);
	}
	
	@After
	public void after() throws Exception {
		if (this.componentTest != null) {
			this.componentTest.deactivate();
		}
		this.componentTest = null;
	}

	@Test
	public void activateDeactivateTest() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase())
		.deactivate();

		assertTrue(true);
	}
	
	@Test
	public void testSerialNumber() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE, ConnectorType.TYPE_2_T) //
				.output(EvcsAbbTerraAc.ChannelId.RATED_POWER, RatedPower.ELEVEN_KW) //
				.output(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK, (short) 11) //
				.output(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR, (short) 25) //
				.output(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER, 0x1234) //
				)
		.deactivate();
	}

	@Test
	public void testFirmwareVersion() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.FIRMWARE_VERSION, 0x01020304L)
				)
		.deactivate();
	}

	@Test
	public void testUserSettableMaxCurrent() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.MAX_CURRENT, 15543000)
				)
		.deactivate();
	}

	@Test
	public void testErrorCode() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.ERROR_CODE, ErrorCodes.INTERNAL_COMMUNICATION_FAILURE)
				)
		.deactivate();
	}

	@Test
	public void testSocketLockState() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE, LockState.CABLE_CONNECTED_CHARGING_STATION_LOCKED_ELECTRIC_VEHICLE)
				)
		.deactivate();
	}

	@Test
	public void testChargingState() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.CHARGING_STATE, 0x0900L)
				)
		.deactivate();

	}

	@Test
	public void testChargingCurrentLimit() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT, 10111)
				)
		.deactivate();
	}

	@Test
	public void testChargingCurrentPhases() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(ElectricityMeter.ChannelId.CURRENT_L1, 6543)
				.output(ElectricityMeter.ChannelId.CURRENT_L2, 6432)
				.output(ElectricityMeter.ChannelId.CURRENT_L3, 6321)
				)
		.deactivate();
	}

	@Test
	public void testVoltage() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 230500)
				.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 231000)
				.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 229000)
				)
		.deactivate();
	}


	@Test
	public void testPower() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 10123)
				)
		.deactivate();
	}

	@Test
	public void testEnergy() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 728531472L)
				)
		.deactivate();
	}

	@Test
	public void testModbusValues() throws Exception {
		this.componentTest.activate(CONFIG)
		.next(new TestCase()//
				.output(EvcsAbbTerraAc.ChannelId.COMMUNICATION_TIMEOUT, 23)
				.output(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT_BY_MODBUS, 14123000)
				.output(EvcsAbbTerraAc.ChannelId.FALLBACK_LIMIT, 12)
				)
		.deactivate();
	}

}