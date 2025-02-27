package org.hyperagents.yggdrasil.eventbus.messageboxes;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.eventbus.codecs.ContextManagementMessageMarshaller;
import org.hyperagents.yggdrasil.eventbus.codecs.GenericMessageCodec;
import org.hyperagents.yggdrasil.eventbus.messages.ContextMessage;
import org.hyperagents.yggdrasil.utils.ContextManagementConfig;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class ContextMessageBox implements Messagebox<ContextMessage> {
    private static final Logger LOGGER = LogManager.getLogger(ContextMessageBox.class);
    
    private final EventBus eventBus;
    private final ContextManagementConfig contextManagementConfig;

    public ContextMessageBox(final EventBus eventBus, final ContextManagementConfig contextManagementConfig) {
        this.eventBus = eventBus;
        this.contextManagementConfig = contextManagementConfig;
    }

    @Override
    public void init() {
        if(this.contextManagementConfig.isEnabled()) {
            this.eventBus.registerDefaultCodec(
                ContextMessage.GetStaticContext.class,
                new GenericMessageCodec<>(ContextMessage.GetStaticContext.class, new ContextManagementMessageMarshaller())
            );
            this.eventBus.registerDefaultCodec(
                ContextMessage.GetProfiledContext.class,
                new GenericMessageCodec<>(ContextMessage.GetProfiledContext.class, new ContextManagementMessageMarshaller())
            );
            this.eventBus.registerDefaultCodec(
                ContextMessage.ValidateContextBasecAccess.class,
                new GenericMessageCodec<>(ContextMessage.ValidateContextBasecAccess.class, new ContextManagementMessageMarshaller())
            );
            this.eventBus.registerDefaultCodec(ContextMessage.ContextStreamUpdate.class, 
                new GenericMessageCodec<>(ContextMessage.ContextStreamUpdate.class, new ContextManagementMessageMarshaller()));
            this.eventBus.registerDefaultCodec(ContextMessage.VerifyContextStreamSubscription.class, 
                new GenericMessageCodec<>(ContextMessage.VerifyContextStreamSubscription.class, new ContextManagementMessageMarshaller()));
        }
        else {
            LOGGER.warn("Context Management is not enabled. The context message exchange will not be initialized.");
        }
    }

    @Override
    public Future<Message<String>> sendMessage(ContextMessage message) {
        if (this.contextManagementConfig.isEnabled()) {
            return this.eventBus.request(MessageAddresses.CONTEXT_SERVICE.getName(), message);
        } else {
            LOGGER.warn("Context Management is not enabled. This message will be a dead letter.");
        }
        final var promise = Promise.<Message<String>>promise();
        promise.complete();
        return promise.future();
    }

    @Override
    public void receiveMessages(Consumer<Message<ContextMessage>> messageHandler) {
        if (this.contextManagementConfig.isEnabled()) {
            this.eventBus.consumer(MessageAddresses.CONTEXT_SERVICE.getName(), messageHandler::accept);
        } else {
            LOGGER.warn("Context Management is not enabled. No messages will be received.");
        }
    }

}
