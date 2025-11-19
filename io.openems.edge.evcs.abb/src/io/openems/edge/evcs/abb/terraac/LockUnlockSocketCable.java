package io.openems.edge.evcs.abb.terraac;

import io.openems.common.types.OptionsEnum;

/**
 * Possible lock unlock cable of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 5.16 of <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a>. 
 */
public enum LockUnlockSocketCable implements OptionsEnum {
	
	UNLOCK(0x0000, "Unlock the cable/connector. Precaution is advised to make sure that no ongoing charging session prior to setting this state"),
	LOCK(0x0001, "Cable will be locked irrespective of charging session until unlock is set or power cycle performed"),
	/*
	 * Internal state if channel has an error or is not ready
	 */
	UNDEFINED(-1, "Channel error or not ready"),
	;
	
	private final int state;
	private final String name;
	
	private LockUnlockSocketCable(final int state, final String name) {
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
		return UNDEFINED;
	}
}
