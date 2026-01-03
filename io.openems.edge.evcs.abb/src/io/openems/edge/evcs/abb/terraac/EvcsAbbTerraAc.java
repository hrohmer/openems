package io.openems.edge.evcs.abb.terraac;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
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
		SERIAL_NUMBER(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.LOW)
				.text("The serial number")),
		PRODUCTION_BLOCK(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.LOW) //
				.text("The production time block")),
		PRODUCTION_DATE_YEAR(Doc.of(OpenemsType.SHORT) //
				.text("The production year")),
		PRODUCTION_DATE_WEEK(Doc.of(OpenemsType.SHORT) //
				.text("The production week of year")),
		SPARE_PLANT(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.LOW) //
				.text("The Plant ID and Spare")),
		TYPE_BLOCK(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("The type block")),
		CONNECTOR_TYPE(Doc.of(ConnectorType.values()) //
				.text("Connector type")),
		RATED_POWER(Doc.of(RatedPower.values()) //
				.text("Rated power")),
		
		FIRMWARE_VERSION(Doc.of(OpenemsType.LONG) //
				.persistencePriority(PersistencePriority.LOW) //
				.text("Firmware version")),

		MAX_CURRENT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("User Settable Max Current")),
		ERROR_CODE(Doc.of(ErrorCodes.values()) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Error Code")),
		SOCKET_LOCK_STATE(Doc.of(LockState.values()) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Socket Lock State")),
		CHARGING_STATE_IDLE(Doc.of(OpenemsType.BOOLEAN) //
				.text("Charging State: Below the commanded value")),
		CHARGING_STATE_B1(Doc.of(OpenemsType.BOOLEAN) //
				.text("State B1 (i.e.) EV Plug in, pending authorization")),
		CHARGING_STATE_B2(Doc.of(OpenemsType.BOOLEAN) //
				.text("State B2 (i.e.) EV Plug in, EVSE ready for charging (PWM)")),
		CHARGING_STATE_C1(Doc.of(OpenemsType.BOOLEAN) //
				.text("State C1 (i.e.) EV Ready for charge, S2 closed (no PWM).")),
		CHARGING_STATE_C2(Doc.of(OpenemsType.BOOLEAN) //
				.text("State C2 (i.e.) Charging Contact closed, energy delivering")),
		CHARGING_STATE_AT_RATED_CURRENT(Doc.of(OpenemsType.BOOLEAN) //
				.text("Charging State: 0 - charging at rated current; 1- below the commanded value")),
		CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Charging Current Limit") ),
		COMMUNICATION_TIMEOUT(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Charging State")),
		CHARGING_CURRENT_LIMIT_BY_MODBUS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Charging current limit set by Modbus")),
		FALLBACK_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.AMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Fallback limit")),

		SET_CHARGING_CURRENT_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.unit(Unit.MILLIAMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Set Charging Current Limit")),
		SET_LOCK_UNLOCK_SOCKET_CABLE(Doc.of(LockUnlockSocketCable.values()) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("This register (4103h) provides an option only for Socket cable to lock or unlock on Charger side")),
		SET_START_STOP(Doc.of(StartStop.values()) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("This register (4105h) provides an option to start or stop a charge session")),
		SET_COMMUNICATION_TIMEOUT(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.unit(Unit.SECONDS)
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Communication timeout")),
		SET_FALLBACK_LIMIT(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.unit(Unit.AMPERE) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Set fallback limit")),
		;

		private final Doc doc;

		private ChannelId(final Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}
