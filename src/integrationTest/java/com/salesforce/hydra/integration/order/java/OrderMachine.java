/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.Hydra;
import com.salesforce.hydra.integration.order.java.OrderDomain.Cancelled;
import com.salesforce.hydra.integration.order.java.OrderDomain.ChargePayment;
import com.salesforce.hydra.integration.order.java.OrderDomain.Charged;
import com.salesforce.hydra.integration.order.java.OrderDomain.Charging;
import com.salesforce.hydra.integration.order.java.OrderDomain.Completed;
import com.salesforce.hydra.integration.order.java.OrderDomain.Notified;
import com.salesforce.hydra.integration.order.java.OrderDomain.NotifyCustomer;
import com.salesforce.hydra.integration.order.java.OrderDomain.Notifying;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderAction;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderEvent;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderState;
import com.salesforce.hydra.integration.order.java.OrderDomain.OutOfStock;
import com.salesforce.hydra.integration.order.java.OrderDomain.PaymentDeclined;
import com.salesforce.hydra.integration.order.java.OrderDomain.ReleaseInventory;
import com.salesforce.hydra.integration.order.java.OrderDomain.ReserveInventory;
import com.salesforce.hydra.integration.order.java.OrderDomain.Reserved;
import com.salesforce.hydra.integration.order.java.OrderDomain.Reserving;
import com.salesforce.hydra.integration.order.java.OrderDomain.SendShipNotice;
import com.salesforce.hydra.integration.order.java.OrderDomain.ShipParcel;
import com.salesforce.hydra.integration.order.java.OrderDomain.Shipped;
import com.salesforce.hydra.integration.order.java.OrderDomain.Shipping;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validated;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validating;

/** The legal moves of an order: which event in which state routes where, with what action. */
final class OrderMachine {

  private OrderMachine() {}

  static Hydra<OrderState, OrderEvent, OrderAction> create() {
    return Hydra.create(
        mb -> {
          mb.initialState(new Validating(0L, "", "", 0));

          mb.state(
              Validating.class,
              sb ->
                  sb.on(
                      Validated.class,
                      (validating, event) ->
                          sb.transitionTo(
                              new Reserving(
                                  validating.amountCents(),
                                  validating.address(),
                                  validating.sku(),
                                  validating.qty()),
                              new ReserveInventory(validating.sku(), validating.qty()))));

          mb.state(
              Reserving.class,
              sb -> {
                sb.on(
                    Reserved.class,
                    (reserving, event) ->
                        sb.transitionTo(
                            new Charging(
                                reserving.amountCents(),
                                reserving.address(),
                                reserving.sku(),
                                reserving.qty()),
                            new ChargePayment(reserving.amountCents())));
                sb.on(
                    OutOfStock.class,
                    (reserving, event) ->
                        sb.transitionTo(
                            new Cancelled("out of stock"), new NotifyCustomer("out of stock")));
              });

          mb.state(
              Charging.class,
              sb -> {
                sb.on(
                    Charged.class,
                    (charging, event) ->
                        sb.transitionTo(
                            new Shipping(charging.amountCents(), charging.address()),
                            new ShipParcel(charging.address())));
                sb.on(
                    PaymentDeclined.class,
                    (charging, event) ->
                        sb.transitionTo(
                            new Cancelled(event.reason()),
                            new ReleaseInventory(charging.sku(), charging.qty())));
              });

          mb.state(
              Shipping.class,
              sb ->
                  sb.on(
                      Shipped.class,
                      (shipping, event) ->
                          sb.transitionTo(
                              new Notifying(shipping.address()),
                              new SendShipNotice(shipping.address()))));

          mb.state(
              Notifying.class,
              sb -> sb.on(Notified.class, (notifying, event) -> sb.transitionTo(new Completed())));

          mb.state(Completed.class, sb -> {});
          mb.state(Cancelled.class, sb -> {});
        });
  }
}
