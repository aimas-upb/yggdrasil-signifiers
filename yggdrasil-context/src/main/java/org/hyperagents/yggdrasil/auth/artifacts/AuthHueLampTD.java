package org.hyperagents.yggdrasil.auth.artifacts;

import java.util.Arrays;

import org.hyperagents.yggdrasil.auth.AuthorizationRegistry;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.auth.model.AuthorizedEntityType;
import org.hyperagents.yggdrasil.auth.model.CASHMERE;
import org.hyperagents.yggdrasil.auth.model.ContextBasedAuthorization;

import cartago.OPERATION;
import cartago.OpFeedbackParam;
import ch.unisg.ics.interactions.wot.td.schemas.ArraySchema;
import ch.unisg.ics.interactions.wot.td.schemas.StringSchema;

/**
 * HueLamp TD Artifact, has an on/off state that can be toggled and a color 
 * that can be set. Serves as exemplary Hypermedia Artifact, uses TD as its ontology
 */

public class AuthHueLampTD extends ContextAuthHypermediaTDArtifact {
  
  private static final String EXAMPLE_PREFIX = "http://example.org/";

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

  @Override
  protected void registerSharedContextAutorizations() {
    // add the Lab308ContextDomainGroup as a shared context requirement in the AuthorisationRegistry
    // First, create the read and write SharedContextAccessAuthorisation object

    // The URI for the ContextDomainGroup 
    ContextBasedAuthorization accessAuth = new ContextBasedAuthorization(getArtifactUri(), 
            Arrays.asList(AuthorizationAccessType.READ, AuthorizationAccessType.WRITE), AuthorizedEntityType.AGENT,
            CASHMERE.accessRequester.stringValue(),
            EXAMPLE_PREFIX + "light308AccessCondition");
    
    // register the authorization object for the artifact
    registerAuthorization(accessAuth);
    
    // add the read and write SharedContextAccessAuthorisation object to the AuthorisationRegistry
    AuthorizationRegistry authRegistry = AuthorizationRegistry.getInstance();
    authRegistry.addContextAuthorisation(getArtifactUri(), accessAuth);
  }
}

