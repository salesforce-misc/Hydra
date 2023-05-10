package org.revcloud.app.domain;

import org.revcloud.app.domain.Action.Cancel;
import org.revcloud.app.domain.Action.PaymentFailed;
import org.revcloud.app.domain.Action.PaymentSuccessful;
import org.revcloud.app.domain.Order.Idle;
import org.revcloud.app.domain.Order.Processed;
import org.revcloud.hydra.Hydra;

public final class OrderMachine {

  private OrderMachine() {}

  public static final Hydra<Order, Action, SideEffect> orderMachine =
      Hydra.create(
          mb -> {
            mb.initialState(Idle.INSTANCE);

            mb.state(
                Idle.class,
                sb ->
                    sb.on(
                        Action.Place.class,
                        (currentState, action) ->
                            sb.transitionTo(Order.Placed.INSTANCE, SideEffect.OnPlaced.INSTANCE)));

            mb.state(
                Order.Placed.class,
                sb -> {
                  sb.on(
                      PaymentFailed.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                  sb.on(
                      PaymentSuccessful.class,
                      (currentState, action) ->
                          sb.transitionTo(Processed.INSTANCE, SideEffect.OnPaid.INSTANCE));
                  sb.on(
                      Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                });

            mb.state(
                Order.Processed.class,
                sb -> {
                  sb.on(
                      Action.Ship.class,
                      (currentState, action) ->
                          sb.transitionTo(Order.Delivered.INSTANCE, SideEffect.OnShipped.INSTANCE));
                  sb.on(
                      Action.Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Order.Idle.INSTANCE, SideEffect.OnCancelled.INSTANCE));
                });
          });
}
