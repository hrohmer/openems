package io.openems.edge.timeofusetariff.entsoe.gridsell;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TariffGridSell;

public interface TariffEntsoeGridSell extends OpenemsComponent, TariffGridSell {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER)//
				.text("Displays the HTTP status code")), //
		CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE)//
				.unit(Unit.MONEY_PER_MEGAWATT_HOUR)//
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current raw ENTSO-E spot price for this timeslot, in the market currency, before exchange-rate conversion and grid fees")), //
		UNABLE_TO_FETCH_MARKET_PRICES(Doc.of(Level.WARNING)//
				.translationKey(TariffEntsoeGridSell.class, "unableToFetchMarketPrices")), //
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
