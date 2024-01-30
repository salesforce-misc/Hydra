package com.salesforce.hydra;

import static com.salesforce.hydra.TimeMachine.Action.BACKWARD;
import static com.salesforce.hydra.TimeMachine.Action.FAST_BACKWARD;
import static com.salesforce.hydra.TimeMachine.Action.FAST_FORWARD;
import static com.salesforce.hydra.TimeMachine.Action.FORWARD;
import static com.salesforce.hydra.TimeMachine.State.FUTURE;
import static com.salesforce.hydra.TimeMachine.State.PAST;
import static com.salesforce.hydra.TimeMachine.State.PRESENT;

public class TimeMachine {
  private final Hydra<String, String, ?> timeMachine =
      Hydra.create(
          mb -> {
            mb.initialState(PRESENT);
            mb.state(
                PRESENT,
                String.class,
                sb -> {
                  sb.on(FORWARD, String.class, (currentState, action) -> sb.transitionTo(FUTURE));
                  sb.on(BACKWARD, String.class, (currentState, action) -> sb.transitionTo(PAST));
                });
            mb.state(
                FUTURE,
                String.class,
                sb -> {
                  sb.on(BACKWARD, String.class, (currentState, action) -> sb.transitionTo(PRESENT));
                  sb.on(
                      FAST_BACKWARD, String.class, (currentState, action) -> sb.transitionTo(PAST));
                });
            mb.state(
                PAST,
                String.class,
                sb -> {
                  sb.on(FORWARD, String.class, (currentState, action) -> sb.transitionTo(PRESENT));
                  sb.on(
                      FAST_FORWARD,
                      String.class,
                      (currentState, action) -> sb.transitionTo(FUTURE));
                });
          });

  static final class State {
    public static final String PAST = "Past";
    public static final String PRESENT = "Present";
    public static final String FUTURE = "Future";
  }

  static final class Action {
    public static final String FORWARD = "Forward";
    public static final String BACKWARD = "Backward";
    public static final String FAST_FORWARD = "FastForward";
    public static final String FAST_BACKWARD = "FastBackward";
  }
}
