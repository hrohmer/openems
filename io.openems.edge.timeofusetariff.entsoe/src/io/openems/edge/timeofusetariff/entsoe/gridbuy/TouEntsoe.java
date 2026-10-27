package io.openems.edge.timeofusetariff.entsoe.gridbuy;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

public interface TouEntsoe extends OpenemsComponent, TimeOfUseTariff {

	/**
	 * Triggers a price update. The method is running asynchronous, so it will take
	 * a few seconds before the new prices are set.
	 */
	void triggerPriceUpdate();

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER)//
				.text("Displays the HTTP status code")), //
		CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE)//
				.unit(Unit.MONEY_PER_MEGAWATT_HOUR)//
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current raw ENTSO-E spot price for this timeslot, in the market currency, before exchange-rate conversion and grid fees")), //
		UNABLE_TO_UPDATE_PRICES(Doc.of(Level.WARNING)//
				.translationKey(TouEntsoe.class, "unableToUpdatePrices")), //
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

}
