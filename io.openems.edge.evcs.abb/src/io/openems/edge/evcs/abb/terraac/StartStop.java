package io.openems.edge.evcs.abb.terraac;

import io.openems.common.types.OptionsEnum;

/**
 * Possible start stop states of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 5.17 of <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a>. 
 */
public enum StartStop implements OptionsEnum {
	
	START(0x0000, "Start charging session"),
	STOP(0x0001, "Stop charging session"),
	;
	
	private final int state;
	private final String name;
	
	private StartStop(final int state, final String name) {
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
		return STOP;
	}
}
