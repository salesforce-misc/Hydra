package org.revcloud.hydra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HydraTest {
  @Test
  @DisplayName("create")
  void create() {
    /*final var hydra = Hydra.<State, Event, SideEffect>create(cb -> {
      cb.<State.Solid>state(Matcher.any(String.class), state -> {
        state.<Event.OnMelted>on(Matcher.any(String.class), event -> {
          state.transitionTo()
        });
      });
    }); */
  }
}
