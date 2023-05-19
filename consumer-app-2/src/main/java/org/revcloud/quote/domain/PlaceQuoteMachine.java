package org.revcloud.quote.domain;

import java.util.Map;
import java.util.Random;
import org.revcloud.hydra.Hydra;
import org.revcloud.quote.domain.Action.TaxQuote;
import org.revcloud.quote.domain.Quote.Idle;

public class PlaceQuoteMachine {
  private PlaceQuoteMachine() {}

  public static final Hydra<Quote, Event, Action> quoteMachine =
      Hydra.create(
          mb -> {
            mb.initialState(Idle.INSTANCE);

            mb.state(
                Quote.Idle.class,
                sb ->
                    sb.on(
                        Event.Place.class,
                        (currentState, event) ->
                            sb.transitionTo(Quote.PersistInProgress.INSTANCE, Action.PersistQuote.INSTANCE)));
            mb.state(
                Quote.PersistInProgress.class,
                sb -> {
                  sb.on(
                      Event.PersistFailed.class,
                      (currentState, event) ->
                          sb.transitionTo(Quote.FailedToPersist.INSTANCE, Action.OnPersistFailed.INSTANCE));
                  sb.on(
                      Event.PersistSuccess.class,
                      (currentState, event) -> {
                        final var persistResult = event.getPersistResult();
                        if (atpForPricing(persistResult)) {
                          return sb.transitionTo(Quote.PricingInProgress.INSTANCE, Action.PriceQuote.INSTANCE);
                        } else {
                          return sb.transitionTo(Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE);
                        }
                      });
                });
            mb.state(
                Quote.PricingInProgress.class,
                sb -> {
                  sb.on(
                      Event.PricingFailed.class,
                      (currentState, event) ->
                          sb.transitionTo(Quote.FailedToPrice.INSTANCE, Action.OnPriceFailed.INSTANCE));
                  sb.on(
                      Event.PricingSuccess.class,
                      (currentState, event) -> {
                        final var prePricing = event.getPrePricing();
                        if (atpForTax(prePricing)) {
                          return sb.transitionTo(Quote.TaxInProgress.INSTANCE, TaxQuote.INSTANCE);
                        } else {
                          return sb.transitionTo(Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE);
                        }
                      }
                  );
                });
            mb.state(
                Quote.TaxInProgress.class,
                sb -> {
                  sb.on(
                      Event.TaxFailed.class,
                      (currentState, event) ->
                          sb.transitionTo(Quote.FailedToTax.INSTANCE, Action.OnPriceFailed.INSTANCE));
                  sb.on(
                      Event.TaxSuccess.class,
                      (currentState, event) ->
                          sb.transitionTo(Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE));
                });
          });

  private static final Random random = new Random();

  static boolean atpForPricing(Map<String, String> persistResult) {
    return random.nextBoolean();
  }

  static boolean atpForTax(Map<String, String> prePricing) {
    return random.nextBoolean();
  }
}
