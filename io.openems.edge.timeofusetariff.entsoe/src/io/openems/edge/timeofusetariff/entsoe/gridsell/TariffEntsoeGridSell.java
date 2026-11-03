package io.openems.edge.timeofusetariff.entsoe.gridsell;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TariffGridSell;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

public interface TariffEntsoeGridSell extends OpenemsComponent, TariffGridSell {

	/**
	 * Returns the current intraday-based grid-sell prices.
	 *
	 * @return current intraday grid-sell prices
	 */
	TimeOfUsePrices getIntradayGridSellPrices();

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER)//
				.text("Displays the HTTP status code")), //
		CURRENT_PRICE_SELL(Doc.of(OpenemsType.DOUBLE)//
				.unit(Unit.MONEY_PER_MEGAWATT_HOUR)//
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current ENTSO-E price for this timeslot, after applying the configured calculation expression")), //
		INTRADAY_PRICE_SELL(Doc.of(OpenemsType.DOUBLE)//
				.unit(Unit.MONEY_PER_MEGAWATT_HOUR)//
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current ENTSO-E intraday price for this timeslot, after applying the configured calculation expression")), //
		UNABLE_TO_FETCH_MARKET_PRICES(Doc.of(Level.WARNING)//
				.translationKey(TariffEntsoeGridSell.class, "unableToFetchMarketPrices")), //
		HTTP_STATUS_CODE_INTRADAY(Doc.of(OpenemsType.INTEGER)//
				.text("Displays the HTTP status code of the last intraday price fetch")), //
		UNABLE_TO_FETCH_INTRADAY_MARKET_PRICES(Doc.of(Level.WARNING)//
				.translationKey(TariffEntsoeGridSell.class, "unableToFetchIntradayMarketPrices")), //
		;

		private final Doc doc;

		ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}
