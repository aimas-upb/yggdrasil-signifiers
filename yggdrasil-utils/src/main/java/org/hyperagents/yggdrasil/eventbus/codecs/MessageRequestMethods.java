package org.hyperagents.yggdrasil.eventbus.codecs;

import java.util.Arrays;
import java.util.Optional;

enum MessageRequestMethods {
  GET_ENTITY("getEntity"),
  GET_ENTITY_IRI("getEntityIri"),
  UPDATE_ENTITY("updateEntity"),
  PATCH_ENTITY("patchEntity"),
  DELETE_ENTITY("deleteEntity"),
  CREATE_WORKSPACE("createWorkspace"),
  CREATE_SUB_WORKSPACE("createSubWorkspace"),
  JOIN_WORKSPACE("joinWorkspace"),
  LEAVE_WORKSPACE("leaveWorkspace"),
  FOCUS("focus"),
  CREATE_ARTIFACT("createArtifact"),
  CREATE_BODY("createBody"),
  DO_ACTION("performAction"),
  GET_WORKSPACES("GetWorkspaces"),
  GET_ARTIFACTS("GetArtifacts"),
  CONTEXT_DOMAIN_REPRESENTATION("ContextDomainRepresentation"),
  QUERY("query"),
  // Context Management
  CONTAINS_ASSERTION("containsAssertion"),
  VALIDATE_CONTEXT_BASED_ACCESS("validateContextBasedAccess"),
  GET_STATIC_CONTEXT("getStaticContext"),
  GET_PROFILED_CONTEXT("getProfiledContext"),
  ADD_STATIC_CONTEXT("addStaticContext"),
  ADD_PROFILED_CONTEXT("addProfiledContext"),
  ADD_CONTEXT_STREAM("addContextStream"),
  REMOVE_CONTEXT_STREAM("removeContextStream"),
  CONTEXT_STREAM_VERIFY_SUBSCRIPTION("contextStreamVerifySubscription"),
  CONTEXT_STREAM_UPDATE("contextStreamUpdate"),
  GET_CONTEXT_STREAM_REPRESENTATION("getContextStreamRepresentation"),
  // WAC
  AUTHORIZE_ACCESS("authorizeAccess"),
  GET_WAC_RESOURCE("getWACResource"),
  ;

  private static final String PREFIX = "org.hyperagents.yggdrasil.eventbus.methods.";

  private final String name;

  MessageRequestMethods(final String name) {
    this.name = PREFIX + name;
  }

  public String getName() {
    return this.name;
  }

  public static Optional<MessageRequestMethods> getFromName(final String name) {
    return Arrays.stream(values()).filter(m -> m.getName().equals(name)).findFirst();
  }
}
