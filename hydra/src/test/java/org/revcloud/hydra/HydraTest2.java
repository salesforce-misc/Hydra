package org.revcloud.hydra;

import org.junit.jupiter.api.Test;
import org.revcloud.hydra.OrderMachine2.PaymentSuccessful;
import org.revcloud.hydra.OrderMachine2.Place;

class HydraTest2 {

  @Test
  void initialStateShouldBeIdle() {
    final var orderMachine = new OrderMachine2().orderMachine;
    orderMachine.transition(Place.INSTANCE);
    orderMachine.transition(PaymentSuccessful.INSTANCE);
  }
}
