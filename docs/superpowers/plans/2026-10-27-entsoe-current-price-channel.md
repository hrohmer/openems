# ENTSO-E Current Price Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the current raw ENTSO-E spot price as a backend-synced Channel on both ENTSO-E
time-of-use components (`TouEntsoeImpl` grid-buy, `TariffEntsoeGridSellImpl` grid-sell), updated
each time a new price fetch succeeds.

**Architecture:** Add a `CURRENT_PRICE` (`OpenemsType.DOUBLE`) channel to each component's own
`ChannelId` enum (not to the shared, non-component `EntsoeMarketPriceProvider`). Write it inside
each component's existing `setPrices(MarketPriceData)` subscriber callback, which already fires on
every successful fetch, using `marketPriceData.getValues().getAt(Instant.now(clock))` — the raw
spot price before any exchange-rate or grid-fee/calculator adjustment.

**Tech Stack:** Java 21, bnd/OSGi Declarative Services, Gradle (bnd workspace plugin), JUnit 5.

## Global Constraints

- Build/test with `JAVA_HOME` pointed at JDK 21 (`/Library/Java/JavaVirtualMachines/jdk-21.0.5+11/Contents/Home`)
  — Gradle itself needs ≥17 but `compileJava` targets release 21, so the daemon must run on 21.
- Follow existing file style exactly: tabs for indentation, trailing `//` on continued
  fluent-chain lines, no new comments beyond what's already there.
- No new scheduling/polling — the channel only updates inside the existing fetch-callback path.
- Design reference: `docs/superpowers/specs/2026-10-27-entsoe-current-price-channel-design.md`

---

## Task 1: Remove the unwired dead-code `ChannelId` from `EntsoeMarketPriceProvider`

**Files:**
- Modify (revert): `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/priceprovider/EntsoeMarketPriceProvider.java`

**Interfaces:**
- Consumes: nothing (pure revert).
- Produces: a clean baseline for Tasks 2–3; `EntsoeMarketPriceProvider.ChannelId` no longer exists afterward (confirmed unused anywhere in the codebase).

This file currently has an **uncommitted** change (confirmed via `git diff`) adding a
`CURRENT_PRICE` `ChannelId` enum to `EntsoeMarketPriceProvider`. It can never work there —
`EntsoeMarketPriceProviderImpl` is a pooled, non-`OpenemsComponent` helper, not registered as an
Edge component, so the channel would never be synced to the backend. Revert it with `git
checkout` rather than hand-editing, since it's clean, uncommitted, and nothing else references it.

- [ ] **Step 1: Confirm the file only has this one uncommitted change**

Run: `git diff --stat -- io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/priceprovider/EntsoeMarketPriceProvider.java`

Expected: one file listed, `+28, -0` (or similar small insertion-only diff — no deletions of
pre-existing content).

- [ ] **Step 2: Revert the file**

Run: `git checkout -- io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/priceprovider/EntsoeMarketPriceProvider.java`

- [ ] **Step 3: Verify the file is back to its committed state and nothing else changed**

Run: `git status --short -- io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/priceprovider/EntsoeMarketPriceProvider.java`

Expected: no output (file matches HEAD).

- [ ] **Step 4: Commit**

```bash
git add io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/priceprovider/EntsoeMarketPriceProvider.java
git commit -m "$(cat <<'EOF'
Revert unwired CURRENT_PRICE channel on EntsoeMarketPriceProvider

EntsoeMarketPriceProviderImpl is a pooled helper, not an
OpenemsComponent, so a channel declared here can never reach the
backend. The real channel is added to TouEntsoe and
TariffEntsoeGridSell instead.
EOF
)"
```

---

## Task 2: Add `CURRENT_PRICE` channel to the grid-buy component (`TouEntsoe`)

**Files:**
- Modify: `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoe.java`
- Modify: `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeImpl.java`
- Test: `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeTest.java`

**Interfaces:**
- Consumes: `MarketPriceData.getValues(): TimeRangeValues<Double>` and
  `TimeRangeValues<Double>.getAt(Instant): Double` (both pre-existing, `io.openems.common.types`/`io.openems.common.utils`).
  `ComponentManager.getClock(): Clock` (pre-existing field `this.componentManager` on `TouEntsoeImpl`).
- Produces: `TouEntsoe.ChannelId.CURRENT_PRICE` — a new enum constant other code/tests can read via
  `component.channel(TouEntsoe.ChannelId.CURRENT_PRICE)` or `ComponentTest.TestCase.output(TouEntsoe.ChannelId.CURRENT_PRICE, ...)`.

- [ ] **Step 1: Write the failing test**

Open `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeTest.java`
and insert the following new test method directly after the existing `getTestEntsoeResponse()`
helper method (i.e. right before `public void testAllDsoSchedules()`):

```java
	@Test
	public void testCurrentPriceChannel() throws Exception {
		final var httpTestBundle = DummyBridgeHttpBundle.of();
		final var clock = new TimeLeapClock(Instant.parse("2026-02-02T00:00:00Z"));
		final var entsoe = new TouEntsoeImpl();
		var dummyMeta = new DummyMeta() //
				.withCurrency(EUR);

		var pool = this.createPool(httpTestBundle.factory());

		new ComponentTest(entsoe) //
				.addReference("meta", dummyMeta) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("entsoeMarketPriceProviderPool", pool) //
				.activate(MyConfig.create() //
						.setId("tou0") //
						.setSecurityToken("Unit-Test") //
						.setBiddingZone(EntsoeBiddingZone.GERMANY) //
						.setAncillaryCosts(buildJsonObject() //
								.addProperty("dso", "BAYERNWERK") //
								.build() //
								.toString()) //
						.setCalculateExpression("") //
						.build())

				.next(new TestCase("Current price channel reflects raw spot price at fetch time") //
						.onBeforeProcessImage(() -> {
							var testResponse = this.getTestEntsoeResponse();
							httpTestBundle.forceNextSuccessfulResult(HttpResponse.ok(testResponse));
							httpTestBundle.runTasksImmediately();
						}) //
						.output(TouEntsoe.ChannelId.CURRENT_PRICE, 99.41) //
				) //
		;
	}
```

No new imports are needed — `TimeLeapClock`, `Instant`, `DummyBridgeHttpBundle`, `HttpResponse`,
`ComponentTest`, `TestCase`, `DummyComponentManager`, `DummyMeta`, `EUR`, `EntsoeBiddingZone`, and
`MyConfig` are all already imported in this file. `99.41` is the known raw spot price at
`2026-02-02T00:00:00Z` from `entsoe-response.xml` — the same value already asserted (as the `x` in
`x+y`) by the existing `testSchedule` test in this file, so it is proven-correct ground truth, not
a guess.

- [ ] **Step 2: Run the test to verify it fails (compile error)**

Run:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :io.openems.edge.timeofusetariff.entsoe:test --tests "io.openems.edge.timeofusetariff.entsoe.gridbuy.TouEntsoeTest"
```

Expected: `FAILURE` — `compileTestJava` fails with `cannot find symbol: variable CURRENT_PRICE`
(or similar), because `TouEntsoe.ChannelId.CURRENT_PRICE` does not exist yet.

- [ ] **Step 3: Add the `CURRENT_PRICE` channel to `TouEntsoe.ChannelId`**

In `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoe.java`,
add the import and the new enum constant:

```java
package io.openems.edge.timeofusetariff.entsoe.gridbuy;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
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
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current raw ENTSO-E spot price for this timeslot")), //
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
```

- [ ] **Step 4: Run the test to verify it now fails on the assertion (not compilation)**

Run the same command as Step 2.

Expected: `FAILURE` — compiles fine now, but the test fails inside `validateOutputs` with something
like `expected ReadValue [99.41] got [null] for Channel [CURRENT_PRICE]`, because
`TouEntsoeImpl` does not write the channel yet.

- [ ] **Step 5: Write the channel inside `TouEntsoeImpl.setPrices(...)`**

In `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeImpl.java`,
change the `setPrices` method from:

```java
	protected void setPrices(MarketPriceData marketPriceData) {
		if (marketPriceData == null) {
			this.prices.set(null);
			return;
		}

		final var globalCurrency = this.meta.getCurrency();
		final double exchangeRate = getExchangeRateOrElse(marketPriceData.getCurrency(), globalCurrency, 1.);
		final var gridFees = this.helper.getPrices();

		final var processedPrices = GridBuyUtils.processPrices(this.componentManager.getClock(), this.priceCalculator,
				marketPriceData.getValues(), exchangeRate, gridFees);
		this.prices.set(processedPrices);
	}
```

to:

```java
	protected void setPrices(MarketPriceData marketPriceData) {
		setValue(this, TouEntsoe.ChannelId.CURRENT_PRICE, marketPriceData == null ? null
				: marketPriceData.getValues().getAt(Instant.now(this.componentManager.getClock())));

		if (marketPriceData == null) {
			this.prices.set(null);
			return;
		}

		final var globalCurrency = this.meta.getCurrency();
		final double exchangeRate = getExchangeRateOrElse(marketPriceData.getCurrency(), globalCurrency, 1.);
		final var gridFees = this.helper.getPrices();

		final var processedPrices = GridBuyUtils.processPrices(this.componentManager.getClock(), this.priceCalculator,
				marketPriceData.getValues(), exchangeRate, gridFees);
		this.prices.set(processedPrices);
	}
```

No new imports are needed — `setValue` (`io.openems.edge.common.channel.ChannelUtils.setValue`)
and `Instant` (`java.time.Instant`) are already statically/regularly imported in this file.

- [ ] **Step 6: Run the test to verify it passes**

Run the same command as Step 2.

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the full existing test class to check for regressions**

Run:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :io.openems.edge.timeofusetariff.entsoe:test --tests "io.openems.edge.timeofusetariff.entsoe.gridbuy.TouEntsoeTest"
```

Expected: `BUILD SUCCESSFUL` (all methods in `TouEntsoeTest`, including the pre-existing
`testHttpFetch`, `testSchedule`, etc., still pass).

- [ ] **Step 8: Commit**

```bash
git add io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoe.java \
        io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeImpl.java \
        io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridbuy/TouEntsoeTest.java
git commit -m "$(cat <<'EOF'
Add CURRENT_PRICE channel to TouEntsoe (grid-buy)

Writes the raw ENTSO-E spot price for the current timeslot into a
new backend-synced channel every time a price fetch succeeds.
EOF
)"
```

---

## Task 3: Add `CURRENT_PRICE` channel to the grid-sell component (`TariffEntsoeGridSell`)

**Files:**
- Create: `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/MyConfig.java`
- Create: `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellTest.java`
- Modify: `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSell.java`
- Modify: `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellImpl.java`

**Interfaces:**
- Consumes: same `MarketPriceData`/`TimeRangeValues`/`ComponentManager.getClock()` as Task 2, this
  time via `TariffEntsoeGridSellImpl`'s pre-existing `this.componentManager` field.
- Produces: `TariffEntsoeGridSell.ChannelId.CURRENT_PRICE`, analogous to Task 2's
  `TouEntsoe.ChannelId.CURRENT_PRICE`.

There is currently no test package/config helper for grid-sell at all (only `gridbuy` and
`priceprovider` have test directories). This task creates the minimal `MyConfig` test builder
first (mirroring `gridbuy/MyConfig.java`, but without `ancillaryCosts` — `gridsell/Config.java` has
no such field), then a new test file that reuses the existing
`gridbuy/entsoe-response.xml` fixture directly (no duplication).

- [ ] **Step 1: Create the test `MyConfig` builder for grid-sell**

Create `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/MyConfig.java`:

```java
package io.openems.edge.timeofusetariff.entsoe.gridsell;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.EntsoeBiddingZone;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String securityToken;
		private EntsoeBiddingZone biddingZone;
		private String calculateExpression;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setSecurityToken(String securityToken) {
			this.securityToken = securityToken;
			return this;
		}

		public Builder setBiddingZone(EntsoeBiddingZone biddingZone) {
			this.biddingZone = biddingZone;
			return this;
		}

		public Builder setCalculateExpression(String calculateExpression) {
			this.calculateExpression = calculateExpression;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String securityToken() {
		return this.builder.securityToken;
	}

	@Override
	public EntsoeBiddingZone biddingZone() {
		return this.builder.biddingZone;
	}

	@Override
	public String calculateExpression() {
		return this.builder.calculateExpression;
	}

}
```

- [ ] **Step 2: Write the failing test**

Create `io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellTest.java`:

```java
package io.openems.edge.timeofusetariff.entsoe.gridsell;

import static io.openems.common.test.TestUtils.createDummyClock;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.google.common.io.Resources;

import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.bridge.http.time.periodic.DummyPeriodicExecutorFactory;
import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.EntsoeBiddingZone;
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

		new ComponentTest(gridSell) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("marketPriceProviderPool", pool) //
				.activate(MyConfig.create() //
						.setId("tariffGridSell0") //
						.setSecurityToken("Unit-Test") //
						.setBiddingZone(EntsoeBiddingZone.GERMANY) //
						.setCalculateExpression("") //
						.build())

				.next(new TestCase("Current price channel reflects raw spot price at fetch time") //
						.onBeforeProcessImage(() -> {
							var testResponse = this.getTestEntsoeResponse();
							httpTestBundle.forceNextSuccessfulResult(HttpResponse.ok(testResponse));
							httpTestBundle.runTasksImmediately();
						}) //
						.output(TariffEntsoeGridSell.ChannelId.CURRENT_PRICE, 99.41) //
				) //
		;
	}
}
```

This loads `entsoe-response.xml` via `TouEntsoeTest.class.getResource(...)`, reusing the gridbuy
test's fixture file rather than duplicating a 1600+ line XML file. `99.41` is the same
proven-correct raw spot price at `2026-02-02T00:00:00Z` used in Task 2. `meta` is deliberately not
added as a reference here — `TariffEntsoeGridSellImpl` only touches `this.meta` inside
`debugLog()`, which this test never calls, and `ComponentTest.addReference` is opt-in reflection,
not an OSGi-cardinality check, so leaving it unset is safe.

- [ ] **Step 3: Run the test to verify it fails (compile error)**

Run:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :io.openems.edge.timeofusetariff.entsoe:test --tests "io.openems.edge.timeofusetariff.entsoe.gridsell.TariffEntsoeGridSellTest"
```

Expected: `FAILURE` — `compileTestJava` fails with `cannot find symbol: variable CURRENT_PRICE`.

- [ ] **Step 4: Add the `CURRENT_PRICE` channel to `TariffEntsoeGridSell.ChannelId`**

In `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSell.java`:

```java
package io.openems.edge.timeofusetariff.entsoe.gridsell;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TariffGridSell;

public interface TariffEntsoeGridSell extends OpenemsComponent, TariffGridSell {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER)//
				.text("Displays the HTTP status code")), //
		CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE)//
				.persistencePriority(PersistencePriority.HIGH)//
				.text("The current raw ENTSO-E spot price for this timeslot")), //
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
```

- [ ] **Step 5: Run the test to verify it now fails on the assertion (not compilation)**

Run the same command as Step 3.

Expected: `FAILURE` — compiles, but fails in `validateOutputs` with
`expected ReadValue [99.41] got [null] for Channel [CURRENT_PRICE]`.

- [ ] **Step 6: Write the channel inside `TariffEntsoeGridSellImpl.setPrices(...)`**

In `io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellImpl.java`,
change:

```java
	private void setPrices(MarketPriceData marketPriceData) {
		if (marketPriceData == null) {
			this.prices.set(null);
			return;
		}

		final var processedPrices = GridSellUtils.processPrices(this.priceCalculator, marketPriceData.getValues());
		this.prices.set(processedPrices);
	}
```

to:

```java
	private void setPrices(MarketPriceData marketPriceData) {
		setValue(this, TariffEntsoeGridSell.ChannelId.CURRENT_PRICE, marketPriceData == null ? null
				: marketPriceData.getValues().getAt(Instant.now(this.componentManager.getClock())));

		if (marketPriceData == null) {
			this.prices.set(null);
			return;
		}

		final var processedPrices = GridSellUtils.processPrices(this.priceCalculator, marketPriceData.getValues());
		this.prices.set(processedPrices);
	}
```

No new imports are needed — `java.time.Instant` is already imported in this file (used by
`getGridSellPrices()`), and `setValue` is already statically imported
(`io.openems.edge.common.channel.ChannelUtils.setValue`).

- [ ] **Step 7: Run the test to verify it passes**

Run the same command as Step 3.

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run the full grid-buy and grid-sell test suites together to check for regressions**

Run:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.0.5+11/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :io.openems.edge.timeofusetariff.entsoe:test
```

Expected: `BUILD SUCCESSFUL` (every test in the bundle passes, including
`EntsoeMarketPriceProviderTest`, `PriceCalculatorXTest`, `PriceCalculatorXYTest`, and both
`TouEntsoeTest` and the new `TariffEntsoeGridSellTest`).

- [ ] **Step 9: Commit**

```bash
git add io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSell.java \
        io.openems.edge.timeofusetariff.entsoe/src/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellImpl.java \
        io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/MyConfig.java \
        io.openems.edge.timeofusetariff.entsoe/test/io/openems/edge/timeofusetariff/entsoe/gridsell/TariffEntsoeGridSellTest.java
git commit -m "$(cat <<'EOF'
Add CURRENT_PRICE channel to TariffEntsoeGridSell (grid-sell)

Writes the raw ENTSO-E spot price for the current timeslot into a
new backend-synced channel every time a price fetch succeeds, same
as TouEntsoe. Also adds the first test scaffolding for this
component (MyConfig builder + ComponentTest-based test).
EOF
)"
```
