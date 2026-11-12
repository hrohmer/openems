package io.openems.edge.timeofusetariff.entsoe.gridsell;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.google.common.io.Resources;

import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.bridge.http.time.periodic.DummyPeriodicExecutorFactory;
import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.EntsoeBiddingZone;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.timeofusetariff.entsoe.gridbuy.TouEntsoeTest;
import io.openems.edge.timeofusetariff.entsoe.priceprovider.EntsoeMarketPriceProviderPoolImpl;

public class TariffEntsoeGridSellTest {

	private EntsoeMarketPriceProviderPoolImpl createPool(BridgeHttpFactory httpBridgeFactory) {
		final var clock = createDummyClock();
		final var dummyOem = new DummyOpenemsEdgeOem();

		var dummyClockProvider = new ClockProvider() {
			@Override
			public Clock getClock() {
				return clock;
			}
		};

		return new EntsoeMarketPriceProviderPoolImpl(//
				dummyOem, //
				dummyClockProvider, //
				httpBridgeFactory, //
				new DummyPeriodicExecutorFactory() //
		) {
		};
	}

	private String getTestEntsoeResponse() throws IOException {
		var resource = TouEntsoeTest.class.getResource("entsoe-response.xml");
		assertNotNull(resource);
		return Resources.toString(resource, StandardCharsets.UTF_8);
	}

	@Test
	public void testCurrentPriceChannel() throws Exception {
		final var httpTestBundle = DummyBridgeHttpBundle.of();
		final var clock = new TimeLeapClock(Instant.parse("2026-02-02T00:00:00Z"));
		final var gridSell = new TariffEntsoeGridSellImpl();

		var pool = this.createPool(httpTestBundle.factory());

		var test = new ComponentTest(gridSell) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("marketPriceProviderPool", pool) //
				.activate(MyConfig.create() //
						.setId("tariffGridSell0") //
						.setSecurityToken("Unit-Test") //
						.setBiddingZone(EntsoeBiddingZone.GERMANY) //
						.setCalculateExpression("x*0.9") //
						.build());

		Channel<Double> channel = gridSell.channel(TariffEntsoeGridSell.ChannelId.CURRENT_PRICE_SELL);

		test.next(new TestCase("Current price channel reflects processed price at fetch time") //
				.onBeforeProcessImage(() -> {
					var testResponse = this.getTestEntsoeResponse();
					httpTestBundle.forceNextSuccessfulResult(HttpResponse.ok(testResponse));
					httpTestBundle.runTasksImmediately();
				}) //
		);
		assertEquals(gridSell.getGridSellPrices().getAt(Instant.now(clock)), channel.getNextValue().get());

		clock.leap(15, ChronoUnit.MINUTES);
		test.next(new TestCase("Current price channel ticks forward 15 minutes later, with no new fetch"));
		assertEquals(gridSell.getGridSellPrices().getAt(Instant.now(clock)), channel.getNextValue().get());
	}
}
