package org.hyperagents.yggdrasil.eventbus.messageboxes;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperagents.yggdrasil.eventbus.codecs.GenericMessageCodec;
import org.hyperagents.yggdrasil.eventbus.codecs.WACMessageMarshaller;
import org.hyperagents.yggdrasil.eventbus.messages.WACMessage;
import org.hyperagents.yggdrasil.utils.WACConfig;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class WACMessageBox implements Messagebox<WACMessage> {

    private static final Logger LOGGER = LogManager.getLogger(ContextMessageBox.class);
    
    private final EventBus eventBus;
    private final WACConfig wacConfig;

    public WACMessageBox(final EventBus eventBus, final WACConfig wacConfig) {
        this.eventBus = eventBus;
        this.wacConfig = wacConfig;
    }

    @Override
    public void init() {
        if (this.wacConfig.isEnabled()) {
            this.eventBus.registerDefaultCodec(
                WACMessage.GetWACResource.class,
                new GenericMessageCodec<>(WACMessage.GetWACResource.class, new WACMessageMarshaller())
            );
            this.eventBus.registerDefaultCodec(
                WACMessage.AuthorizeAccess.class,
                new GenericMessageCodec<>(WACMessage.AuthorizeAccess.class, new WACMessageMarshaller())
            );
        } 
        else {
            LOGGER.warn("WAC is not enabled. The WAC message exchange will not be initialized.");
        }
    }

    @Override
    public Future<Message<String>> sendMessage(WACMessage message) {
        if (this.wacConfig.isEnabled()) {
            return this.eventBus.request(MessageAddresses.WAC_SERVICE.getName(), message);
        } else {
            LOGGER.warn("Web Access Control is not enabled. This message will be a dead letter.");
        }
        final var promise = Promise.<Message<String>>promise();
        promise.complete();
        return promise.future();
    }

    @Override
    public void receiveMessages(Consumer<Message<WACMessage>> messageHandler) {
        if (this.wacConfig.isEnabled()) {
            this.eventBus.consumer(MessageAddresses.WAC_SERVICE.getName(), messageHandler::accept);
        } else {
            LOGGER.warn("Web Access Control is not enabled. No messages will be received.");
        }
    }
}
