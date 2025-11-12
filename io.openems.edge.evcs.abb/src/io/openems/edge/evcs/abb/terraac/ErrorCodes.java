package io.openems.edge.evcs.abb.terraac;

import io.openems.common.types.OptionsEnum;

/**
 * Possible power version of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 6.2 "Troubleshooting table (IEC portfolio)" of <a href="https://library.e.abb.com/public/6a169b3d17bb4cb0beb55f9516353e56/Terra%20AC%20User%20manual%20BCM.V3Y00.0-EN%20V004.pdf">
 * User Manual</a>. 
 */
public enum ErrorCodes implements OptionsEnum {
	
	NONE(0x0000, "No Error"),
	RESIDUAL_CURRENT_DETECTED(0x0002, "There is residual current "
			+ "(30mA AC or 6mA DC) in the charge circuit. Current "
			+ "leaks into the ground"),
	PE_MISSING_OR_SWAP_NEUTRAL_AND_PHASE(0x0004, "The EVSE is not earthed "
			+ "correctly or neutral and phase wires are swapped"),
	OVER_VOLTAGE(0x0008, "The maximum voltage on "
			+ "the power input is too high"),
	UNDER_VOLTAGE(0x0010, "The voltage on the power input is not sufficient"),
	OVER_CURRENT(0x0020, "There is an overload on the EV side"),
	SEVERE_OVER_CURRENT(0x0040, "There is an overload on the EV side"),
	OVER_TEMPERATURE(0x0080, "The internal temperature is too high"),
	POWER_RELAY_FAULT(0x0400, "The relay contact is detected "
			+ "in wrong state or has damage"),
	INTERNAL_COMMUNICATION_FAILURE(0x0800, "The internal boards of the "
			+ "EVSE fail to communicate with each other."),
	E_LOCK_FAILURE(0x1000, "Error to lock / unlock the charge connector"),
	MISSING_PHASE(0x2000, "One or more phases are missing"),
	MODBUS_COMMUNICATION_LOST(0x4000, "The Modus communication is lost.")
	;
	
	private final int state;
	private final String name;
	
	private ErrorCodes(final int state, final String name) {
		this.state = state;
		this.name = name;
	}

	@Override
	public int getValue() {
		return this.state;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return NONE;
	}
}
