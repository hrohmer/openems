package io.openems.edge.evcs.abb.terraac;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static io.openems.edge.evcs.api.EvcsUtils.milliampereToWatt;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.text.MessageFormat;
import java.util.Optional;

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
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.AbstractModbusElement;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusRegisterElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.value.Value;
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
		
		/*
		 * Read the serial number block once
		 */
		getModbusProtocol().addTask(new FC3ReadRegistersTask(0x4000, Priority.HIGH, //
				getSerialNumberModbusRegsiterElement()));
	}

	@Deactivate
	@Override
	protected void deactivate() {
		super.deactivate();
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		this.applyConfig(config);
		if (super.modified(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	
	private void applyConfig(Config config) {
		this.config = config;
		this._setChargingType(ChargingType.AC);
		this._setFixedMaximumHardwarePower(this.getConfiguredMaximumHardwarePower());
		this._setFixedMinimumHardwarePower(this.getConfiguredMinimumHardwarePower());
		this._setMinimumPower(milliampereToWatt(this.config.minHwCurrent(), 3));
		this._setMaximumPower(milliampereToWatt(this.config.maxHwCurrent(), 3));
		this._setPowerPrecision(0.23D);
		this._setPhases(Phases.THREE_PHASE);
		this._setStatus(Status.UNDEFINED);
	}

	@Override
	public void handleEvent(Event event) {
		if (this.config.enabled() && !this.config.readOnly()) {
			switch (event.getTopic()) {
			case TOPIC_CYCLE_EXECUTE_WRITE:
				this.writeHandler.run();
				break;
				
			case TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
				if (ErrorCodes.NONE.compareTo(getErrorChannel().value().asEnum()) != 0) {
					this._setStatus(Status.ERROR);
				} else if ((boolean) this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_IDLE).value().get()) {					
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				} else if ((boolean) this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B1).value().get()) {					
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				} else if ((boolean) this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B2).value().get()) {					
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				} else if ((boolean) this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C1).value().get()) {					
					this._setStatus(Status.READY_FOR_CHARGING);
				} else if ((boolean) this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C2).value().get()) {					
					this._setStatus(Status.CHARGING);
				} else {
					this._setStatus(Status.NOT_READY_FOR_CHARGING);
				}
				logger.warn("Set state to: {}", this.getStatus());
				this._setChargingstationCommunicationFailed(this.getModbusCommunicationFailed());
				
				this._setEnergySession(Optional.of(getActiveConsumptionEnergy().get()).orElse(Long.valueOf(0)).intValue());
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
		logger.info("Got new charging power limit: {}mW", power);
		
		int currentForLoad = 0;
		
		final ConnectorType type = getConnectorType().value().asEnum();
		if (this.config.readOnly()) {
			applyDisplayText("This EVCS is in readonly mode");
		
		} else if (( type == ConnectorType.TYPE_2_S || type == ConnectorType.TYPE_2_T) && 
				getLockState().value().asEnum() != LockState.CABLE_CONNECTED_CHARGING_STATION_LOCKED_ELECTRIC_VEHICLE) {
			applyDisplayText("Socket connection not ready: " + getLockState().value().asEnum().getName());
			
		} else if (getErrorChannel().value().asEnum() != ErrorCodes.NONE) {
			applyDisplayText(getErrorChannel().value().asEnum().getName());
			
		} else {
			final Double currentMilliampere = 1000.0D * (power / this.getPhasesAsInt() / Evcs.DEFAULT_VOLTAGE);
			currentForLoad = Math.min(currentMilliampere.intValue(), this.config.maxHwCurrent());
			this.logger.info("Set current charging limit to: {}mA", currentForLoad);
			applyDisplayText(MessageFormat.format("Loading with {0,number}mA", currentForLoad));
		}
		
		setSetChargingCurrentLimit(currentForLoad);
		setStartStop(power > 0 ? StartStop.START : StartStop.STOP);

		// set timeout to 120 seconds. After 120 seconds without communication the fallback limit is used for charging
		setCommunicationTimeout(120);		
		// set to 50% of current load or min HW Current if less
		setFallbackLimit(Math.max(this.config.minHwCurrent(), Double.valueOf(currentForLoad / 2000).intValue()));
		
		/**
		 * handling for socket lock stuff
		 * TODO
		 */ 
		//setLockUnlockSocketCableLimit();
		
		return currentForLoad > 0;
	}

	@Override
	public boolean pauseChargeProcess() throws Exception {
		return applyChargePowerLimit(0);
	}

	@Override
	public boolean applyDisplayText(String text) throws OpenemsException {
		logger.info("EVCS display text: {}", text);
		return true;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		return 30;
	}

	@Override
	public ChargeStateHandler getChargeStateHandler() {
		return this.chargeStateHandler;
	}

	/*
	 * === Logging ===========================================================
	 */
	
	private Channel<ErrorCodes> getErrorChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.ERROR_CODE);
	}

	private Channel<ConnectorType> getConnectorType() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE);
	}
	
	private Channel<LockState> getLockState() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE);
	}
	
	
	private IntegerReadChannel getChargingCurrentLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT);
	}
	
	private Value<Integer> getChargingCurrentLimit() {
		return getChargingCurrentLimitChannel().value();
	}
	
	private IntegerReadChannel getChargingCurrentLimitModbusChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT_BY_MODBUS);
	}

	private Value<Integer> getChargingCurrentLimitModbus() {
		return getChargingCurrentLimitModbusChannel().value();
	}

	private IntegerWriteChannel getSetChargingCurrentLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_CHARGING_CURRENT_LIMIT);
	}
	
	private void setSetChargingCurrentLimit(Integer chargingCurrent) throws OpenemsNamedException {
		getSetChargingCurrentLimitChannel().setNextWriteValue(chargingCurrent);
	}
	
	private IntegerWriteChannel getSetStartStopChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_START_STOP);
	}
	
	private void setStartStop(StartStop value) throws OpenemsNamedException {
		getSetStartStopChannel().setNextWriteValue(value != null ? value.getValue() : StartStop.STOP.getValue());
	}

	private IntegerWriteChannel getSetCommunicationTimeoutChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_COMMUNICATION_TIMEOUT);
	}
	
	private void setCommunicationTimeout(int value) throws OpenemsNamedException {
		getSetCommunicationTimeoutChannel().setNextWriteValue(value);
	}

	private IntegerWriteChannel getSetFallbackLimitChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_FALLBACK_LIMIT);
	}
	
	private void setFallbackLimit(int value) throws OpenemsNamedException {
		getSetFallbackLimitChannel().setNextWriteValue(value);
	}

	private IntegerWriteChannel getSetLockUnlockSocketCableChannel() {
		return this.channel(EvcsAbbTerraAc.ChannelId.SET_LOCK_UNLOCK_SOCKET_CABLE);
	}
	
	private void setLockUnlockSocketCableLimit(LockUnlockSocketCable value) throws OpenemsNamedException {
		getSetLockUnlockSocketCableChannel().setNextWriteValue(value != null ? value.getValue() : LockUnlockSocketCable.UNLOCK.getValue());
	}

	/*
	 * === Logging ===========================================================
	 */

	@Override
	public String debugLog() {
		return this.config.readOnly() ? 
				"Power: " + this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER).getNextValue().orElse(null) :
				"Power: " + this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER).getNextValue().orElse(null)
				+ "| Set Charging power:" + this.getSetChargePowerLimit().get() //
				+ "| Set Charging current:" + getSetChargingCurrentLimitChannel().getNextWriteValue().orElse(Integer.MIN_VALUE) //
				+ "| Charging current:" + this.getChargingCurrentLimit().get() //
				+ "| Charging current (modbus):" + this.getChargingCurrentLimitModbus().get() //
				+ "| Status:" + this.getStatus().getName() //
				+ "| Error:" + this.getErrorChannel().value().asEnum();
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
		final ModbusProtocol modbusProtocol = this.config.readOnly() ? //
				new ModbusProtocol(this, //
						getDeviceInformationTask(), //
						getDeviceMeasurementTask())
				: //
				new ModbusProtocol(this, //
						getDeviceInformationTask(), //
						getDeviceMeasurementTask(), //
						getDeviceControlTask());
		return modbusProtocol;
	}

	private FC3ReadRegistersTask getDeviceInformationTask() {
		return new FC3ReadRegistersTask(0x4000, Priority.LOW, //
				getSerialNumberModbusRegsiterElement(), //
				this.m(EvcsAbbTerraAc.ChannelId.FIRMWARE_VERSION) //
		);
	}

	private FC3ReadRegistersTask getDeviceMeasurementTask() {
		return new FC3ReadRegistersTask(0x4006, Priority.HIGH, //
				this.m(EvcsAbbTerraAc.ChannelId.MAX_CURRENT), //
				this.m(EvcsAbbTerraAc.ChannelId.ERROR_CODE), //
				this.m(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x000C), //
				this.m(new BitsWordElement(DEVICE_START_ADDRESS | 0x000D, this)) //
					.bit(8, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_IDLE) //
					.bit(9, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B1) //
					.bit(10, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_B2) //
					.bit(11, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C1) //
					.bit(12, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_C2) //
					.bit(15, EvcsAbbTerraAc.ChannelId.CHARGING_STATE_AT_RATED_CURRENT), //
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
	}

	private FC16WriteRegistersTask getDeviceControlTask() {
		return new FC16WriteRegistersTask(0x4100, //
				this.m(EvcsAbbTerraAc.ChannelId.SET_CHARGING_CURRENT_LIMIT), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0102), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_LOCK_UNLOCK_SOCKET_CABLE), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0104), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_START_STOP), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_COMMUNICATION_TIMEOUT), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0107, DEVICE_START_ADDRESS | 0x0108), //
				this.m(EvcsAbbTerraAc.ChannelId.SET_FALLBACK_LIMIT)
		);
	}

	private AbstractModbusElement<?, ?, ?> getSerialNumberModbusRegsiterElement() {
		return this.m(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER_BLOCK).onUpdateCallback(value -> {
			if (value == null) {
				this.channel(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER).setNextValue(null);
				this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR).setNextValue(null);
				this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK).setNextValue(null);
				this.channel(EvcsAbbTerraAc.ChannelId.RATED_POWER).setNextValue(null);
				this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE).setNextValue(null);
			} else {
				final Long serialNumber = Long.class.cast(value);
//				logger.warn("SerialNumberBLock: 0x{}", HexFormat.of().formatHex(ByteBuffer.allocate(Long.BYTES).putLong(serialNumber).array()));
				this.channel(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER).setNextValue(Long.valueOf(serialNumber.longValue() & 0x000000000000FFFF).intValue());
				this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR)
					.setNextValue((serialNumber.longValue() >> 16) & 0xFF);
				this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK)
					.setNextValue((serialNumber.longValue() >> 24) & 0xFF);
				this.channel(EvcsAbbTerraAc.ChannelId.RATED_POWER).setNextValue((serialNumber.longValue() >> 48) & 0xFF);
				this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE).setNextValue((serialNumber.longValue() >> 56) & 0xFF);
			}
		});
	}

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
