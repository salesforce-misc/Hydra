package org.revcloud.app;

import org.junit.jupiter.api.Nested;
import org.revcloud.app.domain.Action;
import org.revcloud.app.domain.Action.Cancel;
import org.revcloud.app.domain.Action.PaymentFailed;
import org.revcloud.app.domain.Action.PaymentSuccessful;
import org.revcloud.app.domain.Order;
import org.revcloud.app.domain.Order.Idle;
import org.revcloud.app.domain.Order.Processed;
import org.revcloud.app.domain.SideEffect;
import org.revcloud.hydra.Hydra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HydraTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(HydraTest.class);

  @Nested
  class OrderMachine {
    private final Hydra<Order, Action, SideEffect> matterMachine =
        Hydra.create(
            mb -> {
              mb.initialState(Idle.INSTANCE);
              mb.state(
                  Idle.class,
                  sb ->
                      sb.on(
                          Action.Place.class,
                          (currentState, event) ->
                              sb.transitionTo(Order.Placed.INSTANCE, SideEffect.OnPlaced.INSTANCE)));
              mb.state(
                  Order.Placed.class,
                  sb -> {
                    sb.on(
                        PaymentFailed.class,
                        (currentState, event) ->
                            sb.transitionTo(Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                    sb.on(
                        PaymentSuccessful.class,
                        (currentState, event) ->
                            sb.transitionTo(Processed.INSTANCE, SideEffect.OnPaid.INSTANCE));
                    sb.on(
                        Cancel.class,
                        (currentState, event) ->
                            sb.transitionTo(Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                  });
              mb.state(
                  Order.Processed.class,
                  sb -> {
                    sb.on(
                        Action.Ship.class,
                        (currentState, event) ->
                            sb.transitionTo(Order.Delivered.INSTANCE, SideEffect.OnShipped.INSTANCE));
                    sb.on(
                        Action.Cancel.class,
                        (currentState, event) ->
                            sb.transitionTo(Order.Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                  });
            });
  }
}
