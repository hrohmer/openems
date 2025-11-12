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
		PRODUCTION_DATE_RAW(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("The production date (year and week)"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0001), ElementToChannelConverter.DIRECT_1_TO_1 ),
		PRODUCTION_DATE_YEAR(Doc.of(OpenemsType.SHORT).accessMode(AccessMode.READ_ONLY).text("The production year"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0001), null ),
		PRODUCTION_DATE_WEEK(Doc.of(OpenemsType.SHORT).accessMode(AccessMode.READ_ONLY).text("The production week of year"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0001), null ),
		CONNECTOR_DATA(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("Connector type / rated power"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0003), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CONNECTOR_TYPE(Doc.of(ConnectorType.values()).accessMode(AccessMode.READ_ONLY).text("Connector type"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0003), null ),
		RATED_POWER(Doc.of(RatedPower.values()).accessMode(AccessMode.READ_ONLY).text("Rated power"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0003), null ),
		// Word 0x0003 has fixed values
		
		FIRMWARE_VERSION(Doc.of(OpenemsType.LONG).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.LOW).text("Firmware version"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0004), ElementToChannelConverter.DIRECT_1_TO_1 ),

		MAX_CURRENT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("User Settable Max Current"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0006), ElementToChannelConverter.DIRECT_1_TO_1 ),
		ERROR_CODE(Doc.of(ErrorCodes.values()).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Error Code"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0008), ElementToChannelConverter.DIRECT_1_TO_1 ),
		SOCKET_LOCK_STATE(Doc.of(LockState.values()).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Socket Lock State"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x000A), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CHARGING_STATE(Doc.of(OpenemsType.LONG).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Charging State"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x000C), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("Charging Current Limit"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x000E), ElementToChannelConverter.DIRECT_1_TO_1 ),
		COMMUNICATION_TIMEOUT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH).text("Charging State"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0020), ElementToChannelConverter.DIRECT_1_TO_1 ),
		CHARGING_CURRENT_LIMIT_BY_MODBUS(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.MILLIAMPERE).persistencePriority(PersistencePriority.HIGH).text("Charging current limit set by Modbus"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0022), ElementToChannelConverter.DIRECT_1_TO_1 ),
		FALLBACK_LIMIT(Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_ONLY).unit(Unit.AMPERE).persistencePriority(PersistencePriority.HIGH).text("Fallback limit"), new UnsignedWordElement(DEVICE_START_ADDRESS | 0x0024), ElementToChannelConverter.DIRECT_1_TO_1 ),

		SET_CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_ONLY).unit(Unit.AMPERE).persistencePriority(PersistencePriority.HIGH).text("Set Charging Current Limit"), new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0100), ElementToChannelConverter.SCALE_FACTOR_MINUS_3 ),
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
	
	public static ElementToChannelConverter catSingleWord() {
		return new ElementToChannelConverter(
			// element -> channel
			value -> {
				return value != null ? ((int)value) & 0x0000FFFF : null;
			},
			// channel -> element
			value -> {
				return value != null ? ((int)value) & 0x0000FFFF : null;
			});
	}
}
