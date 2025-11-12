package io.openems.edge.evcs.abb.terraac;

import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

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
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusRegisterElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.evcs.api.ChargeStateHandler;
import io.openems.edge.evcs.api.ChargingType;
import io.openems.edge.evcs.api.Evcs;
import io.openems.edge.evcs.api.EvcsPower;
import io.openems.edge.evcs.api.ManagedEvcs;
import io.openems.edge.evcs.api.Phases;
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
		TOPIC_CYCLE_EXECUTE_WRITE //
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
		this._setPowerPrecision(230);
		this._setPhases(3);
	}

	@Override
	public void handleEvent(Event event) {
		if (this.config.enabled() && !this.config.readOnly()) {
			switch (event.getTopic()) {
			case TOPIC_CYCLE_EXECUTE_WRITE:
				this.writeHandler.run();
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean pauseChargeProcess() throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean applyDisplayText(String text) throws OpenemsException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getMinimumTimeTillChargingLimitTaken() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ChargeStateHandler getChargeStateHandler() {
		return this.chargeStateHandler;
	}

	/*
	 * === Logging ===========================================================
	 */

	@Override
	public String debugLog() {
		return "Power: " + this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER).getNextValue().orElse(null)
				+ "| Limit:" + this.getSetChargePowerLimit().orElse(null) + "| Status:" + this.getStatus().getName();
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
				this.m(EvcsAbbTerraAc.ChannelId.SERIAL_NUMBER), //
				this.m(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_RAW).onUpdateCallback(value -> {
					if (value == null) {
						this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR).setNextValue(null);
						this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK).setNextValue(null);
					} else {
						Integer production = Integer.class.cast(value);
						this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_WEEK)
								.setNextValue(production.intValue() & 0xFF);
						this.channel(EvcsAbbTerraAc.ChannelId.PRODUCTION_DATE_YEAR)
								.setNextValue((production.intValue() >> 8) & 0xFF);
					}
				}), //
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0002, DEVICE_START_ADDRESS | 0x0002), //
				this.m(EvcsAbbTerraAc.ChannelId.CONNECTOR_DATA).onUpdateCallback(value -> {
					if (value == null) {
						this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE).setNextValue(null);
						this.channel(EvcsAbbTerraAc.ChannelId.RATED_POWER).setNextValue(null);
					} else {
						Integer data = Integer.class.cast(value);
						this.channel(EvcsAbbTerraAc.ChannelId.CONNECTOR_TYPE).setNextValue(data.intValue() & 0xFF);
						this.channel(EvcsAbbTerraAc.ChannelId.RATED_POWER).setNextValue((data.intValue() >> 8) & 0xFF);
					}
				}), //
				this.m(EvcsAbbTerraAc.ChannelId.FIRMWARE_VERSION) //
		);
	}

	private FC3ReadRegistersTask getDeviceMeasurementTask() {
		return new FC3ReadRegistersTask(0x4006, Priority.HIGH, //
				this.m(EvcsAbbTerraAc.ChannelId.MAX_CURRENT), //
				this.m(EvcsAbbTerraAc.ChannelId.ERROR_CODE), //
				this.m(EvcsAbbTerraAc.ChannelId.SOCKET_LOCK_STATE), //
				this.m(EvcsAbbTerraAc.ChannelId.CHARGING_STATE), //
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
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0021, DEVICE_START_ADDRESS | 0x0021), //
				this.m(EvcsAbbTerraAc.ChannelId.CHARGING_CURRENT_LIMIT_BY_MODBUS), //
				this.m(EvcsAbbTerraAc.ChannelId.FALLBACK_LIMIT),
				new DummyRegisterElement(DEVICE_START_ADDRESS | 0x0025, DEVICE_START_ADDRESS | 0x0025) //
				);
	}

	private FC16WriteRegistersTask getDeviceControlTask() {
		return new FC16WriteRegistersTask(DEVICE_START_ADDRESS, //
				this.m(EvcsAbbTerraAc.ChannelId.SET_CHARGING_CURRENT_LIMIT)//
		// TODO add missing setter values
		);
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
