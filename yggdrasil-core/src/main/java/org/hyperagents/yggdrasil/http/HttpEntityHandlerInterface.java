package org.hyperagents.yggdrasil.http;

import io.vertx.ext.web.RoutingContext;

public interface HttpEntityHandlerInterface {

  void handleRedirectWithoutSlash(final RoutingContext routingContext);
  void handleGetEntity(final RoutingContext routingContext);
  void handleCreateWorkspaceJson(final RoutingContext context);
  void handleCreateWorkspaceTurtle(final RoutingContext context);
  void handleCreateArtifactJson(final RoutingContext context);
  void handleCreateArtifactTurtle(final RoutingContext context);
  void handleFocus(final RoutingContext context);
  void handleAction(final RoutingContext context);
  void handleUpdateEntity(final RoutingContext routingContext);
  void handleDeleteEntity(final RoutingContext routingContext);
  void handleEntitySubscription(final RoutingContext routingContext);
  void handleJoinWorkspace(final RoutingContext routingContext);
  void handleLeaveWorkspace(final RoutingContext routingContext);
  void handleCreateSubWorkspace(final RoutingContext context);
  void handleQuery(final RoutingContext routingContext);
}
