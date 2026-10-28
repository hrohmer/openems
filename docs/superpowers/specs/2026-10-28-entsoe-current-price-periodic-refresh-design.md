# ENTSO-E Current Price Periodic Refresh — Design

## Goal

Make the `CURRENT_PRICE` channel on `TouEntsoeImpl` (grid-buy) and `TariffEntsoeGridSellImpl`
(grid-sell) reflect the price of the *actual current* 15-minute timeslot at all times, instead of
only updating once a day when new ENTSO-E data is fetched.

## Background

`CURRENT_PRICE` was added in a previous iteration (see
`docs/superpowers/specs/2026-10-27-entsoe-current-price-channel-design.md`), written only inside
each component's `setPrices(MarketPriceData)` method — itself only invoked when
`EntsoeMarketPriceProvider.getMarketPrices()` emits a new value, which happens once a day (the
ENTSO-E day-ahead auction is only published once a day; see `EntsoeApi.ENTSOE_UPDATE_HOUR`,
`HttpEntsoeDataProvider`). So the channel's value only changes once a day, even though the
underlying fetched data already has a distinct price for every 15-minute slot of the day
(`MarketPriceData.getValues(): TimeRangeValues<Double>`, resolution `PT15M`).

This is purely a channel-refresh-cadence problem. The once-daily HTTP fetch itself is correct and
unchanged by this design — ENTSO-E's day-ahead auction genuinely only publishes once a day, and
re-fetching more often would return the same data. (A second ENTSO-E data source — intraday
auction prices, which publish a few times per day with fresher curves — was discussed as a
follow-up and is explicitly out of scope for this design; it will get its own brainstorm/spec.)

## Decision

Reuse the existing OpenEMS per-cycle event bus rather than inventing new scheduling
infrastructure. OpenEMS fires `EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE` roughly once
per second to every component that implements `org.osgi.service.event.EventHandler` and declares
the topic via `@EventTopics`. `TimeOfUseTariffControllerImpl` already relies on exactly this kind
of mechanism for its own `QUARTERLY_PRICES` channel: no stored "current" value, just a cheap
stateless `TimeRangeValues.getAt(Instant.now())` lookup performed every cycle — the value only
*changes* once per 15 minutes because the underlying data is quantized to 15-minute slots, but
the mechanism itself is "ask the clock every cycle," not a 15-minute timer.

`TouEntsoeImpl` and `TariffEntsoeGridSellImpl` will adopt the same pattern, following the exact
precedent of `GoodWeChargerPv1` (`io.openems.edge.goodwe.charger.singlestring.GoodWeChargerPv1`),
which already implements `EventHandler` with `@EventTopics({
EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE })` in this codebase.

## Changes

### `TouEntsoeImpl` / `TariffEntsoeGridSellImpl`

1. Add `implements EventHandler` to the class declaration.
2. Add `@EventTopics({ EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE })` above the existing
   `@Component(...)` annotation.
3. Add a `handleEvent(Event event)` method that performs the same raw-value computation
   `CURRENT_PRICE` already uses, reading from `this.priceProvider` (which may be `null` if the
   component is disabled or hasn't finished activating) instead of from a `MarketPriceData`
   parameter:

   ```java
   @Override
   public void handleEvent(Event event) {
       var marketPriceData = this.priceProvider == null ? null : this.priceProvider.getMarketPrices().getValue();
       setValue(this, <ChannelId>.CURRENT_PRICE, marketPriceData == null ? null
               : marketPriceData.getValues().getAt(Instant.now(this.componentManager.getClock())));
   }
   ```

4. Remove the now-redundant `CURRENT_PRICE` write from `setPrices(MarketPriceData)` — the
   per-cycle `handleEvent` is a strict superset (same raw-value expression, refreshed far more
   often, and still correct immediately after a new fetch since cycles run roughly every second).
   `setPrices(...)` goes back to only handling the processed/final price (`this.prices`), as
   before `CURRENT_PRICE` was introduced.

No other channel, no scheduling configuration, and no change to the HTTP fetch cadence are
touched.

## Testing

Both existing tests (`TouEntsoeTest.testCurrentPriceChannel`,
`TariffEntsoeGridSellTest.testCurrentPriceChannel`) need no new event-plumbing:
`AbstractComponentTest.next(TestCase)` already dispatches every cycle topic — including
`TOPIC_CYCLE_AFTER_PROCESS_IMAGE` — to any `EventHandler`-implementing component under test
automatically (confirmed by reading `AbstractComponentTest.java`'s `next`/`handleEvent(String)`
methods). Since the existing tests already call `.next(new TestCase()...)` with the HTTP fetch
forced inside `onBeforeProcessImage`, the per-cycle handler fires within the same `.next(...)`
call, after the fetch has already landed — so the existing `99.41` assertions keep working
without modification to the test's structure.

One new test case is added per component to demonstrate the actual behavior this design is for:
after the initial fetch (test clock `2026-02-02T00:00:00Z`, `CURRENT_PRICE` = `99.41`), leap the
test clock forward by exactly one 15-minute slot via `TimeLeapClock.leap(15,
java.time.temporal.ChronoUnit.MINUTES)` — advancing it to `2026-02-02T00:15:00Z` — then call
`.next(new TestCase())` again with **no** new HTTP response forced, and assert `CURRENT_PRICE` is
now `95.0` (the known raw price for that slot in the shared `entsoe-response.xml` fixture, already
used as the `x` in `testSchedule`'s `95.00 + (0.1 * 10)` assertion in `TouEntsoeTest`). This
proves the channel ticks forward on clock time alone, without a new fetch.

## Out of Scope

- Any change to HTTP fetch cadence, `HttpEntsoeDataProvider`, or `EntsoeApi`.
- The intraday auction price feed / second channel (separate follow-up design).
- Any change to the processed/final price (`getPrices()`/`getGridSellPrices()`), which is already
  recomputed independently via its own `AtomicReference` and is not part of this design.
