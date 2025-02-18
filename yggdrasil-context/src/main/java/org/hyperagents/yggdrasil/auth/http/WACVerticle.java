package org.hyperagents.yggdrasil.auth.http;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.hyperagents.yggdrasil.auth.model.AuthorizationAccessType;
import org.hyperagents.yggdrasil.context.http.ContextMgmtVerticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class WACVerticle extends AbstractVerticle {
    public static final String BUS_ADDRESS = "org.hyperagents.yggdrasil.eventbus.wac";
    
    // WAC methods
    public static final String GET_WAC_RESOURCE = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".getWacResource";
    public static final String ADD_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".addAuthorization";
    public static final String REMOVE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".removeAuthorization";
    public static final String VALIDATE_AUTHORIZATION = "org.hyperagents.yggdrasil.eventbus.headers.methods"
        + ".validateAuthorization";

    // keys for the headers of the event bus messages
    public static final String WAC_METHOD = "org.hyperagents.yggdrasil.eventbus.headers.wacMethod";
    public static final String ACCESSED_RESOURCE_URI = "org.hyperagents.yggdrasil.eventbus.headers.accessedResourceUri";
    public static final String ACCESS_TYPE = "org.hyperagents.yggdrasil.eventbus.headers.accessType";
    public static final String AGENT_WEBID = "org.hyperagents.yggdrasil.eventbus.headers.agentWebId";
    public static final String AGENT_NAME = "org.hyperagents.yggdrasil.eventbus.headers.agentName";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WACVerticle.class.getName());
        
    @Override
    public void start() {
        //register the event bus handlers
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(BUS_ADDRESS, this::handleWACRequest);
        
        ShaclSail shaclSail = new ShaclSail(new MemoryStore());
        Repository repo = new SailRepository(shaclSail);
    }

    private void handleWACRequest(Message<String> message) {
        LOGGER.info("Handling WAC Request...");
        String wacMethod = message.headers().get(WAC_METHOD);
         
        switch (wacMethod) {
            case VALIDATE_AUTHORIZATION:
                LOGGER.info("Handling WAC Authorization validation action...");
                validateAuthorization(message);
                break;
            case GET_WAC_RESOURCE:
            case ADD_AUTHORIZATION:
            case REMOVE_AUTHORIZATION:
            default:
                LOGGER.info("WAC method " + wacMethod + " not supported yet");
                throw new UnsupportedOperationException("Not implemented yet");
        }
    }

    private void validateAuthorization(Message<String> message) {
        String agentWebId = message.headers().get(AGENT_WEBID);
        String accessedResourceUri = message.headers().get(ACCESSED_RESOURCE_URI);
        AuthorizationAccessType accessType = AuthorizationAccessType.valueOf(message.headers().get(ACCESS_TYPE));
        LOGGER.info("Validating Authorization for agent " + agentWebId + " to access resource " + accessedResourceUri + " in mode " + accessType);
        
        // forward a call to the ContextMgmtVerticle to validate the authorization
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader(ContextMgmtVerticle.CONTEXT_SERVICE, ContextMgmtVerticle.VALIDATE_CONTEXT_BASED_ACCESS);
        options.addHeader(ContextMgmtVerticle.ACCESS_REQUESTER_URI, agentWebId);
        options.addHeader(ContextMgmtVerticle.ACCESSED_RESOURCE_URI, accessedResourceUri);

        vertx.eventBus().request(ContextMgmtVerticle.BUS_ADDRESS, null, options, reply -> {
            if (reply.succeeded()) {
                // if the reply is successful, return the result of the authorization validation
                message.reply(reply.result().body());
            } else {
                // if the reply is not successful, return false
                message.fail(403, "Authorization validation failed");
            }
        });
    }
}
