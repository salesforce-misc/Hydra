package org.revcloud.quote.domain;

import java.util.Map;
import java.util.Random;
import mu.KLogger;
import org.revcloud.hydra.Hydra;
import org.revcloud.quote.domain.Action.TaxQuote;
import org.revcloud.quote.domain.Quote.Idle;
import org.revcloud.quote.env.Env;
import org.revcloud.quote.repo.StatePersistence;
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance;

import static org.revcloud.quote.domain.QuoteTransitionHandlerKt.quoteTransitionHandler;

public class PlaceQuoteMachine {

  private final RabbitMQInstance rabbitMQInstance;
  private final StatePersistence statePersistence;
  private final Env env;
  private final KLogger kLogger;
  
  private final Hydra<Quote, Event, Action> orderHydra;

  public PlaceQuoteMachine(RabbitMQInstance rabbitMQInstance, StatePersistence statePersistence, Env env, KLogger kLogger) {
    this.rabbitMQInstance = rabbitMQInstance;
    this.statePersistence = statePersistence;
    this.env = env;
    this.kLogger = kLogger;
    orderHydra = prepareOrderHydra();
  }

  private Hydra<Quote, Event, Action> prepareOrderHydra() {
    return Hydra.create(
        mb -> {
          mb.initialState(Idle.INSTANCE);
          mb.state(
              Quote.Idle.class,
              sb ->
                  sb.on(
                      Event.Place.class,
                      (currentState, event) -> {
                        if (event.getQuotePayload().size() > 10) {
                          return sb.transitionTo(
                              Quote.PersistInProgress.INSTANCE,
                              Action.PersistQuoteAsync.INSTANCE);
                        } else {
                          return sb.transitionTo(
                              Quote.PersistInProgress.INSTANCE, Action.PersistQuoteSync.INSTANCE);
                        }
                      }));
          mb.state(
              Quote.PersistInProgress.class,
              sb -> {
                sb.on(
                    Event.PersistFailed.class,
                    (currentState, event) ->
                        sb.transitionTo(
                            Quote.FailedToPersist.INSTANCE, Action.OnPersistFailed.INSTANCE));
                sb.on(
                    Event.PersistSuccess.class,
                    (currentState, event) -> {
                      if (atpForPricing(event.getPrePersist(), event.getPersistResult())) {
                        return sb.transitionTo(
                            Quote.PricingInProgress.INSTANCE, Action.PriceQuote.INSTANCE);
                      } else {
                        return sb.transitionTo(
                            Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE);
                      }
                    });
              });
          mb.state(
              Quote.PricingInProgress.class,
              sb -> {
                sb.on(
                    Event.PricingFailed.class,
                    (currentState, event) ->
                        sb.transitionTo(
                            Quote.FailedToPrice.INSTANCE, Action.OnPriceFailed.INSTANCE));
                sb.on(
                    Event.PricingSuccess.class,
                    (currentState, event) -> {
                      final var prePricing = event.getPrePricing();
                      if (atpForTax(prePricing)) {
                        return sb.transitionTo(Quote.TaxInProgress.INSTANCE, TaxQuote.INSTANCE);
                      } else {
                        return sb.transitionTo(
                            Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE);
                      }
                    });
              });
          mb.state(
              Quote.TaxInProgress.class,
              sb -> {
                sb.on(
                    Event.TaxFailed.class,
                    (currentState, event) ->
                        sb.transitionTo(
                            Quote.FailedToTax.INSTANCE, Action.OnPriceFailed.INSTANCE));
                sb.on(
                    Event.TaxSuccess.class,
                    (currentState, event) ->
                        sb.transitionTo(Quote.Completed.INSTANCE, Action.OnCompleted.INSTANCE));
              });
          mb.onTransition(transition -> quoteTransitionHandler(orderHydra, rabbitMQInstance, statePersistence, env, kLogger, transition));
        });
  }

  private static final Random random = new Random();

  static boolean atpForPricing(Map<String, String> prePersist, Map<String, String> persistResult) {
    return random.nextBoolean();
  }

  static boolean atpForTax(Map<String, String> prePricing) {
    return random.nextBoolean();
  }
}
