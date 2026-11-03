package io.openems.edge.timeofusetariff.entsoe.priceprovider;

import io.openems.common.types.MarketPriceData;
import io.openems.common.utils.BehaviorSubject;

public interface EntsoeMarketPriceProvider extends MarketPriceProvider {
	/**
	 * Returns the entsoe configuration of this price provider.
	 *
	 * @return {@link EntsoeConfiguration} reference
	 */
	public EntsoeConfiguration getConfig();

	/**
	 * Returns the current stored intraday market prices of the last successful
	 * intraday price fetch.
	 *
	 * @return Intraday market prices
	 */
	BehaviorSubject<MarketPriceData> getIntradayPrices();

	/**
	 * Returns the result of the last intraday price fetch.
	 *
	 * @return Intraday update state
	 */
	BehaviorSubject<MarketPriceUpdateEvent> getIntradayUpdateState();
}
