package org.revcloud.order.domain;

import com.salesforce.hydra.Hydra;
import org.revcloud.order.domain.Event.Cancel;
import org.revcloud.order.domain.Event.PaymentFailed;
import org.revcloud.order.domain.Event.PaymentSuccessful;
import org.revcloud.order.domain.Order.Idle;
import org.revcloud.order.domain.Order.Processed;

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
