package org.revcloud.hydra;

import org.jetbrains.annotations.NotNull;

class OrderDomain {

  private OrderDomain() {}

  // Order
  interface Order {}

  static final class Idle implements Order {
    @NotNull public static final Idle INSTANCE;

    private Idle() {}

    static {
      INSTANCE = new Idle();
    }
  }

  static final class Placed implements Order {
    @NotNull public static final Placed INSTANCE;

    private Placed() {}

    static {
      INSTANCE = new Placed();
    }
  }

  static final class Processed implements Order {
    @NotNull public static final Processed INSTANCE;

    private Processed() {}

    static {
      INSTANCE = new Processed();
    }
  }

  static final class Delivered implements Order {
    @NotNull public static final Delivered INSTANCE;

    private Delivered() {}

    static {
      INSTANCE = new Delivered();
    }
  }

  interface Event {}

  static final class Place implements Event {
    @NotNull public static final Place INSTANCE;

    private Place() {}

    static {
      INSTANCE = new Place();
    }
  }

  static final class PaymentFailed implements Event {
    @NotNull public static final PaymentFailed INSTANCE;

    private PaymentFailed() {}

    static {
      INSTANCE = new PaymentFailed();
    }
  }

  static final class PaymentSuccessful implements Event {
    @NotNull public static final PaymentSuccessful INSTANCE;

    private PaymentSuccessful() {}

    static {
      INSTANCE = new PaymentSuccessful();
    }
  }

  static final class Ship implements Event {
    @NotNull public static final Ship INSTANCE;

    private Ship() {}

    static {
      INSTANCE = new Ship();
    }
  }

  static final class Cancel implements Event {
    @NotNull public static final Cancel INSTANCE;

    private Cancel() {}

    static {
      INSTANCE = new Cancel();
    }
  }

  // Action
  interface Action {}

  static final class OnPlaced implements Action {
    @NotNull public static final OnPlaced INSTANCE;

    private OnPlaced() {}

    static {
      INSTANCE = new OnPlaced();
    }
  }

  static final class OnPaid implements Action {
    @NotNull public static final OnPaid INSTANCE;

    private OnPaid() {}

    static {
      INSTANCE = new OnPaid();
    }
  }

  static final class OnShipped implements Action {
    @NotNull public static final OnShipped INSTANCE;

    private OnShipped() {}

    static {
      INSTANCE = new OnShipped();
    }
  }

  static final class OnCancelled implements Action {
    @NotNull public static final OnCancelled INSTANCE;

    private OnCancelled() {}

    static {
      INSTANCE = new OnCancelled();
    }
  }
}
