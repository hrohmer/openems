package io.openems.edge.timeofusetariff.entsoe.priceprovider;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.google.common.io.Resources;

import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpFactory;
import io.openems.common.bridge.http.time.periodic.DummyPeriodicExecutorFactory;
import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.common.types.EntsoeBiddingZone;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.timeofusetariff.entsoe.gridbuy.TouEntsoeTest;

public class EntsoeMarketPriceProviderTest {

	@Test
	public void testPool() {
		var pool = this.createPool();

		var testConfig1and2 = new EntsoeConfiguration(EntsoeBiddingZone.GERMANY, "test");
		var instance1 = pool.get(testConfig1and2);
		var instance2 = pool.get(testConfig1and2);

		var testConfig3 = new EntsoeConfiguration(EntsoeBiddingZone.BELGIUM, "test");
		var instance3 = pool.get(testConfig3);

		assertEquals(instance1, instance2);
		assertNotEquals(instance1, instance3);

		assertEquals(2, pool.initializedProviders.size());

		pool.unget(instance3);
		assertEquals(1, pool.initializedProviders.size());

		pool.unget(instance2);
		assertEquals(1, pool.initializedProviders.size());
		pool.unget(instance1);
		assertEquals(0, pool.initializedProviders.size());

		instance1 = pool.get(testConfig1and2);
		assertEquals(1, pool.initializedProviders.size());
		pool.deactivate();
		assertEquals(0, pool.initializedProviders.size());
	}

	private EntsoeMarketPriceProviderPoolImpl createPool() {
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
				DummyBridgeHttpFactory.ofBridgeImpl(DummyBridgeHttpFactory::dummyEndpointFetcher,
						DummyBridgeHttpFactory::dummyBridgeHttpExecutor), //
				new DummyPeriodicExecutorFactory() //
		);
	}

	@Test
	public void testIntradayFetch() throws Exception {
		final var httpTestBundle = DummyBridgeHttpBundle.of();
		var pool = this.createPool(httpTestBundle.factory());

		var provider = pool.get(new EntsoeConfiguration(EntsoeBiddingZone.GERMANY, "test"));

		var intradayResponse = this.getTestIntradayResponse();
		httpTestBundle.fetcher().addSingleUseEndpointHandler(endpoint -> {
			if (!endpoint.url().contains("contract_MarketAgreement.type=A07")) {
				return null;
			}
			return HttpResponse.ok(intradayResponse);
		});
		httpTestBundle.runTasksImmediately();

		var intradayPrices = provider.getIntradayPrices().getValue();
		assertNotNull(intradayPrices);
		assertEquals(50.0, intradayPrices.getValues().getAt(Instant.parse("2026-02-02T00:00:00Z")), 0.001);
	}

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
		);
	}

	private String getTestIntradayResponse() throws IOException {
		var resource = TouEntsoeTest.class.getResource("entsoe-intraday-response.xml");
		assertNotNull(resource);
		return Resources.toString(resource, StandardCharsets.UTF_8);
	}
}
