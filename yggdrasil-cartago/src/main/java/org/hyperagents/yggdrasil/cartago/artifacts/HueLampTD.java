package org.hyperagents.yggdrasil.cartago.artifacts;

import cartago.OPERATION;
import cartago.OpFeedbackParam;
import ch.unisg.ics.interactions.wot.td.schemas.ArraySchema;
import ch.unisg.ics.interactions.wot.td.schemas.StringSchema;

/**
 * HueLamp TD Artifact, has an on/off state that can be toggled and a color 
 * that can be set. Serves as exemplary Hypermedia Artifact, uses TD as its ontology
 */

public class HueLampTD extends HypermediaTDArtifact {
    
  public void init() {
    this.defineObsProperty("state", "off");
    this.defineObsProperty("color", "blue");
  }

  public void init(final String state, final String color) {
    this.defineObsProperty("state", state);
    this.defineObsProperty("color", color);
  }

  /**
   * Retrieves the internal state of the lamp.
   */
  @OPERATION
  public void getStatus(final OpFeedbackParam<String> state, final OpFeedbackParam<String> color) {
    final var propState = this.getObsProperty("state");
    final var propColor = this.getObsProperty("color");
    state.set(propState.stringValue());
    color.set(propColor.stringValue());
    System.out.println("state is " + state.get() + " and color is " + color.get());
  }

  /**
   * Toggles the internal state of the lamp.
   */
  @OPERATION
  public void toggle() {
    final var prop = this.getObsProperty("state");
    if (prop.stringValue().equals("on")) {
      prop.updateValue("off");
    } else {
      prop.updateValue("on");
    }
    System.out.println("state toggled, current state is " + prop.stringValue());
  }

  /**
   * Sets the internal color of the lamp.
   */
  @OPERATION
  public void setColor(final String color) {
    final var prop = this.getObsProperty("color");
    prop.updateValue(color);
    System.out.println("color set to " + color);
  }

  @Override
  protected void registerInteractionAffordances() {    
    // Register the property affordances as get actions
    this.registerActionAffordance("http://example.org/StatusCommand", "getStatus", "status", 
        null,
        new ArraySchema.Builder()
            .addItem(new StringSchema.Builder().build())
            .addItem(new StringSchema.Builder().build())
            .build());
    
    // Register the action affordances
    this.registerActionAffordance("http://example.org/ToggleCommand", "toggle", "toggle");
    this.registerActionAffordance("http://example.org/ColorCommand", "setColor", "color");
  }

}
