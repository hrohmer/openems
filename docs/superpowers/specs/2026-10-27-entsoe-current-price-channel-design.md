# ENTSO-E Current Price Channel — Design

## Goal

Expose the current ENTSO-E spot price as a Channel, visible in the Edge UI and sent to the
backend, on both ENTSO-E time-of-use tariff components (grid-buy and grid-sell).

## Background

`EntsoeMarketPriceProvider.java` currently has an uncommitted, unwired `ChannelId.CURRENT_PRICE`
enum constant. `EntsoeMarketPriceProviderImpl` (the class implementing that interface) is a plain
pooled helper object, not an `OpenemsComponent` — it is shared across components via
`EntsoeMarketPriceProviderPoolImpl`, keyed by bidding-zone/token, and is never registered as an
Edge component. A channel placed there can never be synced to the backend or shown in the UI.
Nothing in the codebase references `EntsoeMarketPriceProvider.ChannelId`, confirming it is dead
code.

The two real Edge components that do have backend-synced channels are:
- `TouEntsoeImpl` (`io.openems.edge.timeofusetariff.entsoe.gridbuy`), channels declared in
  `TouEntsoe.ChannelId` (e.g. `HTTP_STATUS_CODE`, `UNABLE_TO_UPDATE_PRICES`).
- `TariffEntsoeGridSellImpl` (`io.openems.edge.timeofusetariff.entsoe.gridsell`), channels
  declared in `TariffEntsoeGridSell.ChannelId` (same shape).

Both already subscribe to `EntsoeMarketPriceProvider.getMarketPrices()`, a
`BehaviorSubject<MarketPriceData>` that emits synchronously on every successful ENTSO-E fetch, and
both write channel values from inside their existing subscriber callback (`setPrices(...)`).

## Decisions

1. **Channel owner:** add the new channel to both `TouEntsoe.ChannelId` and
   `TariffEntsoeGridSell.ChannelId` — not to `EntsoeMarketPriceProvider`.
2. **Value:** the raw ENTSO-E spot price for the current timeslot, i.e.
   `marketPriceData.getValues().getAt(Instant.now(clock))` — before exchange-rate conversion or
   any grid-fee/calculator adjustment. This is a single shared concept both components can report
   identically, independent of each component's own currency conversion / calculator expression.
3. **Update trigger:** written inside each component's existing `setPrices(MarketPriceData)`
   method, which already runs on every successful fetch (no new scheduling/polling introduced).
   The value does not tick forward quarter-hour by quarter-hour between fetches — it only changes
   when new ENTSO-E data arrives, by explicit request.
4. **Dead code removal:** revert the uncommitted, unwired `ChannelId` addition in
   `EntsoeMarketPriceProvider.java` via `git checkout` before adding the real channels.

## Channel Definitions

### `TouEntsoe.ChannelId.CURRENT_PRICE`

```java
CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE) //
        .persistencePriority(PersistencePriority.HIGH) //
        .text("The current raw ENTSO-E spot price for this timeslot")), //
```

### `TariffEntsoeGridSell.ChannelId.CURRENT_PRICE`

```java
CURRENT_PRICE(Doc.of(OpenemsType.DOUBLE) //
        .persistencePriority(PersistencePriority.HIGH) //
        .text("The current raw ENTSO-E spot price for this timeslot")), //
```

## Behavior

In `TouEntsoeImpl.setPrices(MarketPriceData marketPriceData)` and
`TariffEntsoeGridSellImpl.setPrices(MarketPriceData marketPriceData)`, after the existing
processing logic, add:

```java
setValue(this, <ChannelId>.CURRENT_PRICE,
        marketPriceData == null ? null
                : marketPriceData.getValues().getAt(Instant.now(this.componentManager.getClock())));
```

- `marketPriceData == null` (no successful fetch yet) → channel value `null`.
- `marketPriceData != null` but "now" falls outside the fetched time range → `getAt(...)` already
  returns `null`, which is valid for a `DOUBLE` channel.
- Otherwise → the raw spot price `Double` for the current timeslot.

Both components already hold a `ComponentManager` reference, so `Instant.now(this.componentManager.getClock())`
is available without new wiring.

## Testing

- `TouEntsoeTest` (existing file, `io.openems.edge.timeofusetariff.entsoe.gridbuy`): add a test
  that activates the component, forces the existing `entsoe-response.xml` fixture through
  `httpTestBundle`, and asserts the `CURRENT_PRICE` channel's next-value equals the known raw spot
  price (`99.41`) at clock time `2026-02-02T00:00:00Z` — the same clock/fixture/value already
  proven correct by the existing `testSchedule` test.
- `TariffEntsoeGridSellTest` (new file, `io.openems.edge.timeofusetariff.entsoe.gridsell`): new
  test scaffolding (a `MyConfig` test-config builder, mirroring `gridbuy/MyConfig.java`, since none
  exists yet for grid-sell) plus a test mirroring the above, reusing the existing
  `gridbuy/entsoe-response.xml` fixture (no need to duplicate the file — load it via
  `TouEntsoeTest.class.getResource("entsoe-response.xml")`), asserting the same `CURRENT_PRICE`
  value.

## Out of Scope

- No periodic/scheduled re-evaluation of the channel between fetches — confirmed as not wanted.
- No change to the processed/final price channels or to `TimeOfUsePrices`/`getPrices()` /
  `getGridSellPrices()`.
- No changes to `EntsoeMarketPriceProviderImpl`/`EntsoeMarketPriceProviderPoolImpl` beyond
  reverting the dead-code `ChannelId` addition.
