package org.revcloud.app.domain;

import org.revcloud.app.domain.Action.Cancel;
import org.revcloud.app.domain.Action.PaymentFailed;
import org.revcloud.app.domain.Action.PaymentSuccessful;
import org.revcloud.app.domain.Order.Idle;
import org.revcloud.app.domain.Order.Process;
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
                        (currentState, event) ->
                            sb.transitionTo(Order.Place.INSTANCE, SideEffect.Placed.INSTANCE)));
            mb.state(
                Order.Place.class,
                sb -> {
                  sb.on(
                      PaymentFailed.class,
                      (currentState, event) ->
                          sb.transitionTo(Idle.INSTANCE, SideEffect.Cancelled.INSTANCE));
                  sb.on(
                      PaymentSuccessful.class,
                      (currentState, event) ->
                          sb.transitionTo(Process.INSTANCE, SideEffect.Paid.INSTANCE));
                  sb.on(
                      Cancel.class,
                      (currentState, event) ->
                          sb.transitionTo(Idle.INSTANCE, SideEffect.Cancelled.INSTANCE));
                });
            mb.state(
                Order.Process.class,
                sb -> {
                  sb.on(
                      Action.Ship.class,
                      (currentState, event) ->
                          sb.transitionTo(Order.Deliver.INSTANCE, SideEffect.Shipped.INSTANCE));
                  sb.on(
                      Action.Cancel.class,
                      (currentState, event) ->
                          sb.transitionTo(Order.Idle.INSTANCE, SideEffect.Cancelled.INSTANCE));
                });
          });
}
