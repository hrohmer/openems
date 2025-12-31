package io.openems.edge.evcs.abb.terraac;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.element.ModbusRegisterElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Interface of the Electric Vehicle Charging System for ABB Terra AC.
 * <br>
 * Please see the <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a> for Modbus RTU - RS485 and TCP/IP documentation from ABB.
 */
public interface EvcsAbbTerraAc extends OpenemsComponent {

	/**
	 * Chapter 4.2. Device Identification (start 0x1000)
	 */
	public static final int DEVICE_START_ADDRESS = 0x4000;

	/**
	 * Enum for channels the ABB Terra AC provides. 
	 */
	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		
		// see Chapter 5.1 Serial Number
		SERIAL_NUMBER(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("The serial number"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0000), ElementToChannelConverter.DIRECT_1_TO_1 ),
		PRODUCTION_BLOCK(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("The production time block"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0001), ElementToChannelConverter.DIRECT_1_TO_1 ),
		PRODUCTION_DATE_YEAR(Doc.of(OpenemsType.SHORT).accessMode(AccessMode.READ_ONLY).text("The production year"), null, null ),
		PRODUCTION_DATE_WEEK(Doc.of(OpenemsType.SHORT).accessMode(AccessMode.READ_ONLY).text("The production week of year"), null, null ),
		SPARE_PLANT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("The Plant ID and Spare"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0002), ElementToChannelConverter.DIRECT_1_TO_1 ),
		TYPE_BLOCK(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("The type block"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0003), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CONNECTOR_TYPE(Doc.of(ConnectorType.values()).accessMode(AccessMode.READ_ONLY).text("Connector type"), null, null ),
		RATED_POWER(Doc.of(RatedPower.values()).accessMode(AccessMode.READ_ONLY).text("Rated power"), null, null ),
		
		FIRMWARE_VERSION(Doc.of(OpenemsType.LONG).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("Firmware version"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0004), ElementToChannelConverter.DIRECT_1_TO_1 ),

		MAX_CURRENT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("User Settable Max Current"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0006), ElementToChannelConverter.DIRECT_1_TO_1 ),
		ERROR_CODE(Doc.of(ErrorCodes.values()).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Error Code"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0008), ElementToChannelConverter.DIRECT_1_TO_1 ),
		SOCKET_LOCK_STATE(Doc.of(LockState.values()).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Socket Lock State"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x000A), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CHARGING_STATE_IDLE(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("Charging State: Below the commanded value"), null, null),
		CHARGING_STATE_B1(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("State B1 (i.e.) EV Plug in, pending authorization"), null, null),
		CHARGING_STATE_B2(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("State B2 (i.e.) EV Plug in, EVSE ready for charging (PWM)"), null, null),
		CHARGING_STATE_C1(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("State C1 (i.e.) EV Ready for charge, S2 closed (no PWM)."), null, null),
		CHARGING_STATE_C2(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("State C2 (i.e.) Charging Contact closed, energy delivering"), null, null),
		CHARGING_STATE_AT_RATED_CURRENT(Doc.of(OpenemsType.BOOLEAN).accessMode(AccessMode.READ_ONLY).text("Charging State: 0 - charging at rated current; 1- below the commanded value"), null, null),
		CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("Charging Current Limit"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x000E), ElementToChannelConverter.DIRECT_1_TO_1 ),
		COMMUNICATION_TIMEOUT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Charging State"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0020), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CHARGING_CURRENT_LIMIT_BY_MODBUS(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("Charging current limit set by Modbus"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0022), ElementToChannelConverter.DIRECT_1_TO_1 ),
		FALLBACK_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.AMPERE).persistencePriority(PersistencePriority.HIGH).text("Fallback limit"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0024), ElementToChannelConverter.DIRECT_1_TO_1 ),

		SET_CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.WRITE_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("Set Charging Current Limit"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0100), ElementToChannelConverter.DIRECT_1_TO_1 ),
		SET_LOCK_UNLOCK_SOCKET_CABLE(Doc.of(LockUnlockSocketCable.values()).accessMode(AccessMode.WRITE_ONLY).persistencePriority(PersistencePriority.HIGH).text("This register (4103h) provides an option only for Socket cable to lock or unlock on Charger side"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0103), ElementToChannelConverter.DIRECT_1_TO_1),
		SET_START_STOP(Doc.of(StartStop.values()).accessMode(AccessMode.WRITE_ONLY).persistencePriority(PersistencePriority.HIGH).text("This register (4105h) provides an option to start or stop a charge session"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0105), ElementToChannelConverter.DIRECT_1_TO_1),
		SET_COMMUNICATION_TIMEOUT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.WRITE_ONLY).persistencePriority(PersistencePriority.HIGH).text("Communication timeout"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0106), ElementToChannelConverter.DIRECT_1_TO_1),
		SET_FALLBACK_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.WRITE_ONLY).unit(Unit.AMPERE).persistencePriority(PersistencePriority.HIGH).text("Set fallback limit"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0109), ElementToChannelConverter.DIRECT_1_TO_1),
		;

		private final Doc doc;
		private final ModbusRegisterElement<?, ?> address;
		private final ElementToChannelConverter converter;

		private ChannelId(final Doc doc, final ModbusRegisterElement<?, ?> address, final ElementToChannelConverter converter) {
			this.doc = doc;
			this.address = address;
			this.converter = converter;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}

		public ModbusRegisterElement<?, ?> address() {
			return address;
		}

		public ElementToChannelConverter converter() {
			return converter;
		}
	}
}
