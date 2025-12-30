package io.openems.edge.evcs.abb.terraac;

import java.util.stream.Stream;

import io.openems.common.types.OptionsEnum;

/**
 * Possible power version of the ABB Terra AC electric vehicle charging station.
 * <br>
 * See chapter 5.1 of <a href="https://library.e.abb.com/public/982c2befa2734d259e66d76fa4a7ba77/ABB_Terra_AC_Charger_ModbusCommunication_v1.11.pdf">
 * TAC Modbus Communication</a>. 
 */
public enum RatedPower implements OptionsEnum {
	
	SEVEN_KW(0x0007, "7 kW"),
	ELEVEN_KW(0x0011, "11 kW"),
	TWENTYTWO_KW(0x0022, "22 kW"),
	/*
	 * Internal state if channel has an error or is not ready
	 */
	UNDEFINED(-1, "Channel error or not ready"),
	;
	
	private final int state;
	private final String name;
	
	private RatedPower(final int state, final String name) {
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
		return UNDEFINED;
	}
	
	/**
	 * Helper Method to get the Enum item out if its value.
	 * 
	 * @param value the value to look for
	 * @return the Enum item of {@link #UNDEFINED}
	 */
	public static RatedPower byValue(int value) {
		return Stream.of(RatedPower.values()) //
				.filter(rp -> rp.getValue() == value).findAny() //
				.orElse(UNDEFINED);
	}
}
