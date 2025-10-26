package io.openems.edge.timeofusetariff.entsoe;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

public interface TouEntsoe extends OpenemsComponent, TimeOfUseTariff {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		UNABLE_TO_UPDATE_PRICES(Doc.of(Level.WARNING) //
				.text("Unable to update prices from ENTSO-E API")), //
		
		CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE).accessMode(AccessMode.READ_ONLY).persistencePriority(PersistencePriority.HIGH) //
				.text("The current price from ENTSO-E API")), //
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
