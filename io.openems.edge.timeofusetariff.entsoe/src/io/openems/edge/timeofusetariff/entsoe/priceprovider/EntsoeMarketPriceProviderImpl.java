package io.openems.edge.timeofusetariff.entsoe.priceprovider;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.thirdparty.entsoe.EntsoeApi;
import io.openems.common.bridge.http.time.DefaultDelayTimeProvider;
import io.openems.common.bridge.http.time.DelayTimeProvider.Delay;
import io.openems.common.bridge.http.time.periodic.PeriodicExecutorFactory;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.oem.OpenemsEdgeOem;
import io.openems.common.types.MarketPriceData;
import io.openems.common.utils.BehaviorSubject;
import io.openems.common.utils.StringUtils;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.utils.TimeOfUseDelayTimeProvider;
import io.openems.edge.timeofusetariff.entsoe.priceprovider.provider.EntsoeDataProvider;
import io.openems.edge.timeofusetariff.entsoe.priceprovider.provider.HttpEntsoeDataProvider;

public class EntsoeMarketPriceProviderImpl implements EntsoeMarketPriceProvider, MarketPriceProvider {
	private final Logger log = LoggerFactory.getLogger(EntsoeMarketPriceProviderImpl.class);

	private final EntsoeConfiguration config;
	private final BehaviorSubject<MarketPriceData> priceData = new BehaviorSubject<>(null);
	private final BehaviorSubject<MarketPriceUpdateEvent> updateState = new BehaviorSubject<>(null);
	private final BehaviorSubject<MarketPriceData> intradayPriceData = new BehaviorSubject<>(null);
	private final BehaviorSubject<MarketPriceUpdateEvent> intradayUpdateState = new BehaviorSubject<>(null);

	private final OpenemsEdgeOem oem;
	private final Supplier<Clock> clockSupplier;
	private final BridgeHttpFactory httpBridgeFactory;

	private boolean activated = false;
	private EntsoeDataProvider dataProvider;
	private EntsoeDataProvider intradayDataProvider;
	private Object delayedDataProviderCheckObject;

	EntsoeMarketPriceProviderImpl(EntsoeConfiguration config, OpenemsEdgeOem oem, Supplier<Clock> clockSupplier,
			BridgeHttpFactory httpBridgeFactory, PeriodicExecutorFactory periodicExecutorFactory) {
		this.config = config;

		this.oem = oem;
		this.clockSupplier = clockSupplier;
		this.httpBridgeFactory = httpBridgeFactory;
	}

	synchronized void activate() {
		this.initializeDataProvider();
		this.checkForDataProviderDelayed();
		this.activated = true;
	}

	synchronized void deactivate() {
		this.activated = false;

		if (this.dataProvider != null) {
			this.dataProvider.deactivate();
			this.dataProvider.dispose();
			this.dataProvider = null;
		}
		if (this.intradayDataProvider != null) {
			this.intradayDataProvider.deactivate();
			this.intradayDataProvider.dispose();
			this.intradayDataProvider = null;
		}
	}

	private void checkForDataProviderDelayed() {
		var checkObj = new Object();
		this.delayedDataProviderCheckObject = checkObj;

		CompletableFuture.delayedExecutor(1, TimeUnit.MINUTES).execute(() -> {
			if (this.delayedDataProviderCheckObject == checkObj) {
				this.delayedDataProviderCheckObject = null;
				if (this.dataProvider == null && this.activated) {
					this.log.error("Missing data provider. Is backend correctly loaded?");
				}
			}
		});
	}

	private void initializeDataProvider() {
		this.initializeHttpProvider();
		this.initializeIntradayHttpProvider();
	}

	private void initializeHttpProvider() {
		var apiKey = this.config.entsoeApiKey();
		if (StringUtils.isNullOrEmpty(apiKey)) {
			apiKey = this.oem.getEntsoeToken();
		}
		if (StringUtils.isNullOrEmpty(apiKey)) {
			throw new RuntimeException(
					"Can't fetch entso-e data. No security token is configured and no backend connection is available.");
		}

		this.log.info("Using http connection to fetch market price data from ENTSO-E API.");
		this.dataProvider = new HttpEntsoeDataProvider(this.httpBridgeFactory, apiKey, this.clockSupplier.get(),
				this.config.biddingZone(), this::onDataUpdate, "A01",
				new TimeOfUseDelayTimeProvider(this.clockSupplier.get(), LocalTime.of(EntsoeApi.ENTSOE_UPDATE_HOUR, 0)));
		this.dataProvider.activate();
	}

	private void initializeIntradayHttpProvider() {
		var apiKey = this.config.entsoeApiKey();
		if (StringUtils.isNullOrEmpty(apiKey)) {
			apiKey = this.oem.getEntsoeToken();
		}
		if (StringUtils.isNullOrEmpty(apiKey)) {
			throw new RuntimeException(
					"Can't fetch entso-e data. No security token is configured and no backend connection is available.");
		}

		this.log.info("Using http connection to fetch intraday market price data from ENTSO-E API.");
		this.intradayDataProvider = new HttpEntsoeDataProvider(this.httpBridgeFactory, apiKey,
				this.clockSupplier.get(), this.config.biddingZone(), this::onIntradayDataUpdate, "A07",
				new DefaultDelayTimeProvider<TimeOfUsePrices>(//
						Delay::immediate, //
						error -> Delay.of(Duration.ofMinutes(10)), //
						result -> Delay.of(Duration.ofHours(1)) //
				));
		this.intradayDataProvider.activate();
	}

	private void onDataUpdate(MarketPriceUpdateEvent event) {
		switch (event) {
		case MarketPriceUpdateEvent.Successful(var data) -> this.setMarketPriceData(data);
		case MarketPriceUpdateEvent.FailedWithHttpError(var httpStatus, var httpBody) -> {
			this.log.warn("Unable to Update Entsoe Time-Of-Use Price (Status Code %d): %s".formatted(httpStatus.code(),
					httpBody));
		}
		case MarketPriceUpdateEvent.FailedWithException(var exception) -> {
			if (exception instanceof ExecutionException ex && ex.getCause() instanceof OpenemsNamedException oex) {
				this.log.error("Unable to Update Entsoe Time-Of-Use Price: " + oex.getMessage());
			} else {
				this.log.error("Unable to Update Entsoe Time-Of-Use Price", exception);
			}
		}
		}

		this.updateState.setValue(event);
	}

	private void setMarketPriceData(MarketPriceData marketPriceData) {
		this.log.info("Received new entsoe prices. Range: %s - %s".formatted(marketPriceData.getValues().getFirstTime(),
				marketPriceData.getValues().getEndTime()));

		this.priceData.setValue(marketPriceData);
	}

	private void onIntradayDataUpdate(MarketPriceUpdateEvent event) {
		switch (event) {
		case MarketPriceUpdateEvent.Successful(var data) -> this.setIntradayMarketPriceData(data);
		case MarketPriceUpdateEvent.FailedWithHttpError(var httpStatus, var httpBody) -> {
			this.log.warn("Unable to Update Entsoe Intraday Price (Status Code %d): %s"
					.formatted(httpStatus.code(), httpBody));
		}
		case MarketPriceUpdateEvent.FailedWithException(var exception) -> {
			if (exception instanceof ExecutionException ex && ex.getCause() instanceof OpenemsNamedException oex) {
				this.log.error("Unable to Update Entsoe Intraday Price: " + oex.getMessage());
			} else {
				this.log.error("Unable to Update Entsoe Intraday Price", exception);
			}
		}
		}

		this.intradayUpdateState.setValue(event);
	}

	private void setIntradayMarketPriceData(MarketPriceData marketPriceData) {
		this.log.info("Received new entsoe intraday prices. Range: %s - %s".formatted(
				marketPriceData.getValues().getFirstTime(), marketPriceData.getValues().getEndTime()));

		this.intradayPriceData.setValue(marketPriceData);
	}

	@Override
	public EntsoeConfiguration getConfig() {
		return this.config;
	}

	@Override
	public void triggerPriceUpdate() {
		if (this.dataProvider != null) {
			this.log.info("Triggering price update ...");
			this.dataProvider.deactivate();
			this.dataProvider.activate();
		}
	}

	@Override
	public BehaviorSubject<MarketPriceData> getMarketPrices() {
		return this.priceData;
	}

	@Override
	public BehaviorSubject<MarketPriceUpdateEvent> getUpdateState() {
		return this.updateState;
	}

	@Override
	public BehaviorSubject<MarketPriceData> getIntradayPrices() {
		return this.intradayPriceData;
	}

	@Override
	public BehaviorSubject<MarketPriceUpdateEvent> getIntradayUpdateState() {
		return this.intradayUpdateState;
	}
}
