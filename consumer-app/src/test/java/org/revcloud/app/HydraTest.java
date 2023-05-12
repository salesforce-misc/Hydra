package org.revcloud.app;

import org.junit.jupiter.api.Nested;
import org.revcloud.app.domain.Event;
import org.revcloud.app.domain.Event.Cancel;
import org.revcloud.app.domain.Event.PaymentFailed;
import org.revcloud.app.domain.Event.PaymentSuccessful;
import org.revcloud.app.domain.Order;
import org.revcloud.app.domain.Order.Idle;
import org.revcloud.app.domain.Order.Processed;
import org.revcloud.app.domain.Action;
import org.revcloud.hydra.Hydra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HydraTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(HydraTest.class);

  @Nested
  class OrderMachine {
    private final Hydra<Order, Event, Action> matterMachine =
        Hydra.create(
            mb -> {
              mb.initialState(Idle.INSTANCE);
              mb.state(
                  Idle.class,
                  sb ->
                      sb.on(
                          Event.Place.class,
                          (currentState, event) ->
                              sb.transitionTo(Order.Placed.INSTANCE, Action.OnPlaced.INSTANCE)));
              mb.state(
                  Order.Placed.class,
                  sb -> {
                    sb.on(
                        PaymentFailed.class,
                        (currentState, event) ->
                            sb.transitionTo(Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                    sb.on(
                        PaymentSuccessful.class,
                        (currentState, event) ->
                            sb.transitionTo(Processed.INSTANCE, Action.OnPaid.INSTANCE));
                    sb.on(
                        Cancel.class,
                        (currentState, event) ->
                            sb.transitionTo(Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                  });
              mb.state(
                  Order.Processed.class,
                  sb -> {
                    sb.on(
                        Event.Ship.class,
                        (currentState, event) ->
                            sb.transitionTo(Order.Delivered.INSTANCE, Action.OnShipped.INSTANCE));
                    sb.on(
                        Event.Cancel.class,
                        (currentState, event) ->
                            sb.transitionTo(Order.Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                  });
            });
  }
}
