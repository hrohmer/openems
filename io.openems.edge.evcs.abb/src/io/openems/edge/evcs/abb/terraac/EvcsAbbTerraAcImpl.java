package io.openems.edge.evcs.abb.terraac;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static io.openems.edge.evcs.api.EvcsUtils.milliampereToWatt;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusRegisterElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.BooleanReadChannel;
import io.openems.edge.common.channel.EnumReadChannel;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.evcs.api.ChargeStateHandler;
import io.openems.edge.evcs.api.ChargingType;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.EvcsPower;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.Phases;
import io.openems.edge.evcs.api.Status;
import io.openems.edge.evcs.api.WriteHandler;
import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Implementation of the ABB Terra AC electrical car charging system.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Evcs.AbbTerraAc", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_EXECUTE_WRITE, //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE
})
public class EvcsAbbTerraAcImpl extends AbstractOpenemsModbusComponent implements Evcs, ElectricityMeter, ManagedEvcs,
		OpenemsComponent, ModbusComponent, EventHandler, EvcsAbbTerraAc {

	private final Logger logger = LoggerFactory.getLogger(EvcsAbbTerraAcImpl.class);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private EvcsPower evcsPower;

	/**
	 * Handles charge states.
	 */
	private final ChargeStateHandler chargeStateHandler = new ChargeStateHandler(this);

	/**
	 * Processes the controller's writes to this evcs component.
	 */
	private final WriteHandler writeHandler = new WriteHandler(this);

	private Config config;

	public EvcsAbbTerraAcImpl() {
		super(OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				ManagedEvcs.ChannelId.values(),//
				Evcs.ChannelId.values(), //
				EvcsAbbTerraAc.ChannelId.values()//
		);
	}

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		this.applyConfig(config);
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		/*
		 * Calculates the maximum and minimum hardware power dynamically by listening on
		 * the fixed hardware limit and the phases used for charging
		 */
		Evcs.addCalculatePowerLimitListeners(this);
		
		getSetDisplayTextChannel().onSetNextWrite(s -> logger.info("New display text: {}", s));
		
		if (!config.readOnly()) {
			// make sure the energy limit is reset
			this.getSetEnergyLimitChannel().getNextWriteValueAndReset();
			
			// set the start/stop flag initially
			setStartStop(StartStop.STOP);
			isInPauseChargeProcess.set(getConfiguredDebugMode());
			// set ABB fallback values initially
			setAbbTerraAcFallback();
		}
	}

	@Deactivate
	@Override
	protected void deactivate() {
		super.deactivate();
		
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		final boolean readOnly = this.config != null ? this.config.readOnly() : false;
		
		this.applyConfig(config);
		
		// in case the readonly flag changes, the modbus protocol needs to be updated
		if (config.enabled() && readOnly != config.readOnly()) {
			if (config.readOnly()) {
				getModbusProtocol().removeTask(deviceControlTask);
			} else {
				getModbusProtocol().addTask(deviceControlTask);
			}
		}
		
		if (super.modified(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	
	private void applyConfig(Config config) {
		this.config = config;
		final Phases phases = Phases.THREE_PHASE;
		this._setChargingType(ChargingType.AC);
		this._setPhases(phases);
		this._setFixedMaximumHardwarePower(this.getConfiguredMaximumHardwarePower());
		this._setFixedMinimumHardwarePower(this.getConfiguredMinimumHardwarePower());
		this._setMinimumPower(milliampereToWatt(this.config.minHwCurrent(), phases.getValue()));
		this._setMaximumPower(milliampereToWatt(this.config.maxHwCurrent(), phases.getValue()));
		this._setPowerPrecision(0.23D);
		this._setStatus(Status.UNDEFINED);
	}

	@Override
	public void handleEvent(Event event) {
		if (this.config.enabled()) {
			switch (event.getTopic()) {
			case TOPIC_CYCLE_EXECUTE_WRITE:
				if (!this.config.readOnly()) {
					this.writeHandler.run();
				}
				break;
				
			case TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
				if (ErrorCodes.NONE.compareTo(getError()) != 0) {
					this._setStatus(Status.ERROR);
				
				} else if (isChargingStateIdle()) {					
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				} else if (isChargingStateB1()) {					
					this._setStatus(Status.CHARGING_REJECTED);
				} else if (isChargingStateB2()) {
					// if in state B2 and connector is cable the state is 'charging' most likely
					if (getConnectorType().compareTo(ConnectorType.TYPE_1_P) == 0 || 
							getConnectorType().compareTo(ConnectorType.TYPE_2_G) == 0) {
						this._setStatus(Status.CHARGING);
					} else {
						this._setStatus(Status.READY_FOR_CHARGING);						
					}
				} else if (isChargingStateC1()) {					
					this._setStatus(Status.READY_FOR_CHARGING);
				} else if (isChargingStateC2()) {					
					this._setStatus(Status.CHARGING);
				} else {
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				}				

				this._setChargingstationCommunicationFailed(this.getModbusCommunicationFailed());
				// This register (401Eh = Active Consumption Energy) provides the transferred energy of the current charging session.
				this._setEnergySession(Optional.ofNullable(getActiveConsumptionEnergy().get()).orElse(Long.valueOf(0)).intValue());
				break;
			}
		}

	}

	@Override
	public EvcsPower getEvcsPower() {
		return this.evcsPower;
	}

	@Override
	public int getConfiguredMinimumHardwarePower() {
		return Math.round(this.config.minHwCurrent() / 1000f) * DEFAULT_VOLTAGE * Phases.THREE_PHASE.getValue();
	}

	@Override
	public int getConfiguredMaximumHardwarePower() {
		return Math.round(this.config.maxHwCurrent() / 1000f) * DEFAULT_VOLTAGE * Phases.THREE_PHASE.getValue();
	}

	@Override
	public boolean getConfiguredDebugMode() {
		return this.config.debugMode();
	}

	@Override
	public boolean applyChargePowerLimit(int power) throws Exception {
		logger.info("Got new charging power limit: {} W", power);
		
		
		if (power > 0 && isInPauseChargeProcess.get()) {
			setStartStop(StartStop.START);
			isInPauseChargeProcess.set(false);
			
		}
		
		int currentForLoad = 0;
		
//		final ConnectorType type = getConnectorType().value().asEnum();
//		} else if (( type == ConnectorType.TYPE_2_S || type == ConnectorType.TYPE_2_T) && 
//				getLockState().value().asEnum() != LockState.CABLE_CONNECTED_CHARGING_STATION_LOCKED_ELECTRIC_VEHICLE) {
//			applyDisplayText("Socket connection not ready: " + getLockState().value().asEnum().getName());
//			
//		} else if (getErrorChannel().value().asEnum() != ErrorCodes.NONE) {
//			applyDisplayText(getErrorChannel().value().asEnum().getName());
//			
//		} else {
			final Double currentMilliampere = 1000.0D * (power / this.getPhasesAsInt() / Evcs.DEFAULT_VOLTAGE);
			currentForLoad = Math.min(currentMilliampere.intValue(), this.config.maxHwCurrent());
			this.logger.info("Set current charging limit to: {} mA", currentForLoad);
//			applyDisplayText(MessageFormat.format("Loading with {0,number}mA", currentForLoad));
//		}
		
		setSetChargingCurrentLimit(currentForLoad);

		/**
		 * handling for socket lock stuff
		 * TODO
		 */ 
		//setLockUnlockSocketCableLimit();
		
		return true;
	}

	final AtomicBoolean isInPauseChargeProcess = new AtomicBoolean(false);
	
	@Override
	public boolean pauseChargeProcess() throws Exception {
		
		if (!isInPauseChargeProcess.get()) {
			setStartStop(StartStop.STOP);
			isInPauseChargeProcess.set(true);
			return applyChargePowerLimit(0);
		}
		
		return true;
	}

	@Override
	public boolean applyDisplayText(String text) {
		try {
			setDisplayText(text);
		} catch (OpenemsNamedException e) {
		}
		return false;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		return 30;
	}

	
	@Override
	public boolean isReadOnly() {
		return this.config != null ? this.config.readOnly() : false; 
	}

	@Override
	public ChargeStateHandler getChargeStateHandler() {
		return this.chargeStateHandler;
	}

	private void setAbbTerraAcFallback() throws OpenemsNamedException {
		if (config.enabled() && !config.readOnly()) {

			// set timeout to 120 seconds. After 120 seconds without communication the fallback limit is used for charging
			setCommunicationTimeout(120);		
			
			// set to 50% between min and max HW Current if less
			setFallbackLimit(Double.valueOf((this.config.minHwCurrent() + this.config.maxHwCurrent()) / 2000.0d).intValue());
			
			
		}
	}
	
	/*
	 * === Channels ===========================================================
	 */
	
	private IntegerReadChannel getMaxCurrentChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.MAX_CURRENT);
	}
	
	private Integer getMaxCurrent() {
		return getMaxCurrentChannel().value().orElse(null);
	}
	

	private EnumReadChannel getErrorChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.ERROR_CODE);
	}

	private ErrorCodes getError() {
		return getErrorChannel().value().asEnum();
	}

	
	private EnumReadChannel getLockStateChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE);
	}
	
	private LockState getLockState() {
		return getLockStateChannel().value().asEnum();
	}
	
	
	private IntegerReadChannel getChargingCurrentLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT);
	}
	
	private Integer getChargingCurrentLimit() {
		return getChargingCurrentLimitChannel().value().orElse(null);
	}
	

	private IntegerReadChannel getChargingCurrentLimitModbusChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT_BY_MODBUS);
	}

	private Integer getChargingCurrentLimitModbus() {
		return getChargingCurrentLimitModbusChannel().value().orElse(null);
	}


	private IntegerWriteChannel getSetChargingCurrentLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_CHARGING_CURRENT_LIMIT);
	}
	
	private void setSetChargingCurrentLimit(Integer chargingCurrent) throws OpenemsNamedException {
		getSetChargingCurrentLimitChannel().setNextWriteValue(chargingCurrent);
	}
	
	private Integer getSetChargingCurrentLimit() {
		return getSetChargingCurrentLimitChannel().value().orElse(null);
	}
	
	
	private EnumWriteChannel getSetStartStopChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_START_STOP);
	}
	
	private void setStartStop(StartStop value) throws OpenemsNamedException {
		getSetStartStopChannel().setNextWriteValue(value != null ? value.getValue() : StartStop.STOP.getValue());
	}

	
	private IntegerWriteChannel getSetCommunicationTimeoutChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_COMMUNICATION_TIMEOUT);
	}
	
	private void setCommunicationTimeout(Integer value) throws OpenemsNamedException {
		getSetCommunicationTimeoutChannel().setNextWriteValue(value);
	}

	
	private IntegerWriteChannel getSetFallbackLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_FALLBACK_LIMIT);
	}
	
	private void setFallbackLimit(Integer value) throws OpenemsNamedException {
		getSetFallbackLimitChannel().setNextWriteValue(value);
	}

	private EnumWriteChannel getSetLockUnlockSocketCableChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_LOCK_UNLOCK_SOCKET_CABLE);
	}
	
	private void setLockUnlockSocketCable(LockUnlockSocketCable value) throws OpenemsNamedException {
		getSetLockUnlockSocketCableChannel().setNextWriteValue(value != null ? value.getValue() : LockUnlockSocketCable.UNLOCK.getValue());
	}

	private IntegerWriteChannel getProductionYearChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR);
	}
	
	private void setProductionYear(int value) {
		getProductionYearChannel().setNextValue(value);
	}
	
	private IntegerWriteChannel getProductionWeekChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK);
	}
	
	private void setProductionWeek(int value) {
		getProductionWeekChannel().setNextValue(value);
	}
	
	private EnumWriteChannel getRatedPowerChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.RATED_POWER);
	}
	
	private RatedPower getRatedPower() {
		return getRatedPowerChannel().value().asEnum();
	}
	
	private void setRatedPower(RatedPower value) {
		getRatedPowerChannel().setNextValue(value != null ? value.getValue() : RatedPower.UNDEFINED.getValue());
	}
	
	
	private EnumReadChannel getConnectorTypeChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE);
	}
	
	private void setConnectorType(ConnectorType value) {
		getConnectorTypeChannel().setNextValue(value != null ? value.getValue() : ConnectorType.UNDEFINED.getValue());
	}
	private ConnectorType getConnectorType() {
		return getConnectorTypeChannel().value().asEnum();
	}
	
	
	private BooleanReadChannel getChargingStateIdleChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_IDLE);
	}
	
	private boolean isChargingStateIdle() {
		return getChargingStateIdleChannel().value().orElse(Boolean.FALSE);
	}

	private BooleanReadChannel getChargingStateB1Channel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B1);
	}
	
	private boolean isChargingStateB1() {
		return getChargingStateB1Channel().value().orElse(Boolean.FALSE);
	}

	private BooleanReadChannel getChargingStateB2Channel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B2);
	}
	
	private boolean isChargingStateB2() {
		return getChargingStateB2Channel().value().orElse(Boolean.FALSE);
	}

	private BooleanReadChannel getChargingStateC1Channel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C1);
	}
	
	private boolean isChargingStateC1() {
		return getChargingStateC1Channel().value().orElse(Boolean.FALSE);
	}

	private BooleanReadChannel getChargingStateC2Channel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C2);
	}
	
	private boolean isChargingStateC2() {
		return getChargingStateC2Channel().value().orElse(Boolean.FALSE);
	}

	
	/*
	 * === Logging ===========================================================
	 */

	@Override
	public String debugLog() {
		final StringBuilder sb = new StringBuilder() //
			.append("Power: ").append(this.getActivePower().orElse(null)) //
			.append("| Max current: ").append(this.getMaxCurrent()) //
			.append("| RatedPower: ").append(this.getRatedPower()) //
			.append("| Connector type: ").append(this.getConnectorType()) //
			.append("| Status:").append(this.getStatus()) //
			.append("| Error:").append(this.getError()) //
			;
		if (!this.config.readOnly()) { 
				sb.append("| Set Charging power: ").append(this.getSetChargePowerLimit().orElse(null)) //
					.append("| Set Charging current: ").append(getSetChargingCurrentLimit()) //
					.append("| Charging current: ").append(this.getChargingCurrentLimit()) //
					.append("| Charging current (modbus): ").append(this.getChargingCurrentLimitModbus()) //
					;
		}
		return sb.toString();
	}

	@Override
	public void logDebug(String message) {
		if (this.config.debugMode()) {
			this.logInfo(logger, message);
		}

	}

	/*
	 * === Modbus definition ===================================================
	 */

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		final ModbusProtocol modbusProtocol = new ModbusProtocol(this);
		
		modbusProtocol.addTask(deviceInformationTask);
		modbusProtocol.addTask(deviceTypeTask);
		modbusProtocol.addTask(deviceMeasurementTask);
		if (!this.config.readOnly()) {
			modbusProtocol.addTask(deviceControlTask);
		}
		return modbusProtocol;
	}

	private final FC3ReadRegistersTask deviceInformationTask = new FC3ReadRegistersTask(0x4000, Priority.LOW, //
				this.m(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER).onUpdateCallback(v -> {
					if (v != null && v instanceof Integer) {
						final Integer value = Integer.class.cast(v);
						logger.info("Serial number: 0x{}", Integer.toHexString(value));
					}					
				}),
				this.m(EvcsAbbTerraAc.ChannelId.PRODUCTION_BLOCK).onUpdateCallback(v -> {
					if (v != null && v instanceof Integer) {
						final Integer value = Integer.class.cast(v);
						logger.info("Production block: 0x{}", Integer.toHexString(value));
						setProductionYear(value & 0xFF);
						setProductionWeek((value >> 16) & 0xFF);
					}
				}),
				this.m(EvcsAbbTerraAc.ChannelId.SPARE_PLANT).onUpdateCallback(v -> {
					if (v != null && v instanceof Integer) {
						final Integer value = Integer.class.cast(v);
						logger.info("PlantID & Spare: 0x{}", Integer.toHexString(value));
					}					
				}));
	
	private final FC3ReadRegistersTask deviceTypeTask = new FC3ReadRegistersTask(0x4003, Priority.HIGH, //
				this.m(EvcsAbbTerraAc.ChannelId.TYPE_BLOCK).onUpdateCallback(v -> {
					if (v != null && v instanceof Integer) {
						final Integer value = Integer.class.cast(v);
						logger.info("Type block: 0x{}", Integer.toHexString(value));
						setRatedPower(RatedPower.byValue(value & 0xFF));
						setConnectorType(ConnectorType.byValue((value >> 16) & 0xFF));
					}
				}),
				this.m(EvcsAbbTerraAc.ChannelId.FIRMWARE_VERSION).onUpdateCallback(v -> {
					if (v != null && v instanceof Long) {
						final Long value = Long.class.cast(v);
						logger.info("Firmware version: 0x{}", Long.toHexString(value));						
					}
				}) //
		);

	private final FC3ReadRegistersTask deviceMeasurementTask = new FC3ReadRegistersTask(0x4006, Priority.HIGH, //
				this.m(EvcsAbbTerraAc.ChannelId.MAX_CURRENT), //
				this.m(EvcsAbbTerraAc.ChannelId.ERROR_CODE), //
				this.m(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x000C), //
				this.m(new BitsWordElement(DEVICE_START_ADDRESS | 0x000D, this) //
					.bit(8, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_IDLE) //
					.bit(9, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B1) //
					.bit(10, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B2) //
					.bit(11, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C1) //
					.bit(12, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C2) //
					.bit(15, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_AT_RATED_CURRENT)), //
				this.m(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT), //
				this.m(ElectricityMeter.ChannelId.CURRENT_L1,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0010),
						ElementToChannelConverter.DIRECT_1_TO_1), //
				this.m(ElectricityMeter.ChannelId.CURRENT_L2,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0012),
						ElementToChannelConverter.DIRECT_1_TO_1), //
				this.m(ElectricityMeter.ChannelId.CURRENT_L3,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0014),
						ElementToChannelConverter.DIRECT_1_TO_1), //
				this.m(ElectricityMeter.ChannelId.VOLTAGE_L1,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0016),
						ElementToChannelConverter.SCALE_FACTOR_2),
				this.m(ElectricityMeter.ChannelId.VOLTAGE_L2,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x0018),
						ElementToChannelConverter.SCALE_FACTOR_2),
				this.m(ElectricityMeter.ChannelId.VOLTAGE_L3,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x001A),
						ElementToChannelConverter.SCALE_FACTOR_2),
				this.m(ElectricityMeter.ChannelId.ACTIVE_POWER,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x001C),
						ElementToChannelConverter.DIRECT_1_TO_1), //
				this.m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY,
						new UnsignedDoublewordElement(DEVICE_START_ADDRESS | 0x001E),
						ElementToChannelConverter.DIRECT_1_TO_1), //
				this.m(EvcsAbbTerraAc.ChannelId.COMMUNICATION_TIMEOUT), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0021), //
				this.m(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT_BY_MODBUS), //
				this.m(EvcsAbbTerraAc.ChannelId.FALLBACK_LIMIT),
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0025) //
				);

	private final FC16WriteRegistersTask deviceControlTask = new FC16WriteRegistersTask(0x4100, //
				this.m(EvcsAbbTerraAc.ChannelId.SET_CHARGING_CURRENT_LIMIT), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0102), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_LOCK_UNLOCK_SOCKET_CABLE), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0104), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_START_STOP), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_COMMUNICATION_TIMEOUT), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0107, DEVICE_START_ADDRESS | 0x0108), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_FALLBACK_LIMIT)
		);

	/**
	 * Helper method to create a modbus register by a
	 * {@link EvcsAbbTerraAc.ChannelId}.
	 * 
	 * @param channelId The channel description
	 * @return the element parameter
	 */
	private ModbusRegisterElement<?, ?> m(EvcsAbbTerraAc.ChannelId channelId) {
		return channelId.converter() != null //
				? this.m(channelId, channelId.address(), channelId.converter()) //
				: this.m(channelId, channelId.address());
	}
}
