package org.revcloud.hydra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.revcloud.hydra.Event.OnCondensed;
import org.revcloud.hydra.Event.OnFrozen;
import org.revcloud.hydra.Event.OnMelted;
import org.revcloud.hydra.Event.OnVaporized;
import org.revcloud.hydra.State.Gas;
import org.revcloud.hydra.State.Liquid;
import org.revcloud.hydra.State.Solid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class HydraTest {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(HydraTest.class);
  
  @Nested
  class MatterMachine {
    private final Hydra<State, Event, SideEffect> matterMachine = Hydra.create(mb -> {
      mb.initialState(Solid.INSTANCE);
      mb.state(Solid.class, sb -> sb.on(OnMelted.class, (currentState, event) -> sb.transitionTo(Liquid.INSTANCE)));
      mb.state(Liquid.class, sb -> {
        sb.on(OnFrozen.class, (currentState, event) -> sb.transitionTo(Solid.INSTANCE));
        sb.on(OnVaporized.class, (currentState, event) -> sb.transitionTo(Gas.INSTANCE));
        sb.onEnter((state, event) -> LOGGER.info("Entered State: {}, with Event: {}", state, event));
      });
      mb.state(Gas.class, sb -> sb.on(OnCondensed.class, (currentState, event) -> sb.transitionTo(Liquid.INSTANCE)));
      mb.onTransition(tr -> LOGGER.info(tr.toString()));
    });
    
    @Test
    @DisplayName("Valid transition")
    void validTransition() {
      assertThat(matterMachine.getState()).isEqualTo(Solid.INSTANCE);
      matterMachine.transition(OnMelted.INSTANCE);
      assertThat(matterMachine.getState()).isEqualTo(Liquid.INSTANCE);
    }
  }
  
}
