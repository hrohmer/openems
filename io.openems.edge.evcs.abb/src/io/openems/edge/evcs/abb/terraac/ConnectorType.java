package io.openems.edge.evcs.abb.terraac;

import io.openems.common.types.OptionsEnum;

/**
 * Possible connector type of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 5.1 of <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a>. 
 */
public enum ConnectorType implements OptionsEnum {
	
	TYPE_2_G(0x47, "Type 2 Cable represented as G"),
	TYPE_1_P(0x50, "Type 1 Cable represented as P"),
	TYPE_2_S(0x53, "Type 2 Socket with Shutter represented as S"),
	TYPE_2_T(0x54, "Type 2 Socket represented as T"),
	;
	
	private final int state;
	private final String name;
	
	private ConnectorType(final int state, final String name) {
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
		return TYPE_2_G;
	}
}
