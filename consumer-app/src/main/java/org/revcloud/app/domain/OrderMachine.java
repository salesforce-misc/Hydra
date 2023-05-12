package org.revcloud.app.domain;

import org.revcloud.app.domain.Event.Cancel;
import org.revcloud.app.domain.Event.PaymentFailed;
import org.revcloud.app.domain.Event.PaymentSuccessful;
import org.revcloud.app.domain.Order.Idle;
import org.revcloud.app.domain.Order.Processed;
import org.revcloud.hydra.Hydra;

public final class OrderMachine {

  private OrderMachine() {}

  public static final Hydra<Order, Event, Action> orderMachine =
      Hydra.create(
          mb -> {
            mb.initialState(Idle.INSTANCE);

            mb.state(
                Idle.class,
                sb ->
                    sb.on(
                        Event.Place.class,
                        (currentState, action) ->
                            sb.transitionTo(Order.Placed.INSTANCE, Action.OnPlaced.INSTANCE)));

            mb.state(
                Order.Placed.class,
                sb -> {
                  sb.on(
                      PaymentFailed.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                  sb.on(
                      PaymentSuccessful.class,
                      (currentState, action) ->
                          sb.transitionTo(Processed.INSTANCE, Action.OnPaid.INSTANCE));
                  sb.on(
                      Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                });

            mb.state(
                Order.Processed.class,
                sb -> {
                  sb.on(
                      Event.Ship.class,
                      (currentState, action) ->
                          sb.transitionTo(Order.Delivered.INSTANCE, Action.OnShipped.INSTANCE));
                  sb.on(
                      Event.Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Order.Idle.INSTANCE, Action.OnCancelled.INSTANCE));
                });
          });
}
