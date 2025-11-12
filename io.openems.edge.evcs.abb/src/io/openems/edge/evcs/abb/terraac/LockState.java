package io.openems.edge.evcs.abb.terraac;

import io.openems.common.types.OptionsEnum;

/**
 * Possible lock states of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 5.5 of <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a>. 
 */
public enum LockState implements OptionsEnum {
	
	NO_CABLE(0x0000, "No cable is plugged"),
	CABLE_CONNECTED_CHARGING_STATION_NOT_LOCKED(0x0001, //
			"Cable is connected to the charging station unlocked"),
	CABLE_CONNECTED_CHARGING_STATION_LOCKED(0x0011, //
			"Cable is connected to the charging station locked"),
	CABLE_CONNECTED_CHARGING_STATION_NOT_LOCKED_ELECTRIC_VEHICLE(0x0101,
			"Cable is connected to the charging station and the electric vehicle, unlocked in charging station"),
	CABLE_CONNECTED_CHARGING_STATION_LOCKED_ELECTRIC_VEHICLE(0x0111,
			"Cable is connected to the charging station and the electric vehicle, locked in charging station"),
	;
	
	private final int state;
	private final String name;
	
	private LockState(final int state, final String name) {
		this.state = state;
		this.name = name;
	}

	public int state() {
		return this.state;
	}

	@Override
	public int getValue() {
		return state();
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return NO_CABLE;
	}
}
