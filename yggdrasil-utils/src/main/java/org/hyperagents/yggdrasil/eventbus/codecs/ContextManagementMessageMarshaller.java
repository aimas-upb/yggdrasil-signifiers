package org.hyperagents.yggdrasil.eventbus.codecs;

import java.lang.reflect.Type;

import org.hyperagents.yggdrasil.eventbus.messages.ContextMessage;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ContextManagementMessageMarshaller 
    implements JsonSerializer<ContextMessage>, JsonDeserializer<ContextMessage> {

    @Override
    public ContextMessage deserialize(final JsonElement json, final Type type, final JsonDeserializationContext context)
            throws JsonParseException {
        final var jsonObject = json.getAsJsonObject();
        
        return switch (
            MessageRequestMethods.getFromName(
                            jsonObject.get(MessageFields.REQUEST_METHOD.getName()).getAsString()
                        )
                        .orElseThrow(
                            () -> new JsonParseException("The request method is not valid")
                        )
        ) {
            case VALIDATE_CONTEXT_BASED_ACCESS -> new ContextMessage.ValidateContextBasecAccess(
                jsonObject.get(MessageFields.ACCESS_REQUESTER_URI.getName()).getAsString(),
                jsonObject.get(MessageFields.ACCESSED_RESOURCE_URI.getName()).getAsString()
            );
            case GET_STATIC_CONTEXT -> new ContextMessage.GetStaticContext();
            case GET_PROFILED_CONTEXT -> new ContextMessage.GetProfiledContext(
                jsonObject.get(MessageFields.CONTEXT_ASSERTION_TYPE.getName()).getAsString()
            );
            default -> throw new JsonParseException("The request method is not valid");
        };
    }

    @Override
    public JsonElement serialize(ContextMessage contextMsg, Type type, JsonSerializationContext jsonContext) {
        final var jsonObject = new JsonObject();
        switch(contextMsg) {
            case ContextMessage.ValidateContextBasecAccess validateContextBasecAccess -> {
                jsonObject.addProperty(MessageFields.REQUEST_METHOD.getName(), MessageRequestMethods.VALIDATE_CONTEXT_BASED_ACCESS.getName());
                jsonObject.addProperty(MessageFields.ACCESS_REQUESTER_URI.getName(), validateContextBasecAccess.accessRequesterURI());
                jsonObject.addProperty(MessageFields.ACCESSED_RESOURCE_URI.getName(), validateContextBasecAccess.accessedResourceURI());
            }
            case ContextMessage.GetStaticContext getStaticContext -> {
                jsonObject.addProperty(MessageFields.REQUEST_METHOD.getName(), MessageRequestMethods.GET_STATIC_CONTEXT.getName());
            }
            case ContextMessage.GetProfiledContext getProfiledContext -> {
                jsonObject.addProperty(MessageFields.REQUEST_METHOD.getName(), MessageRequestMethods.GET_PROFILED_CONTEXT.getName());
                jsonObject.addProperty(MessageFields.CONTEXT_ASSERTION_TYPE.getName(), getProfiledContext.contextAssertionType());
            }
        }

        return jsonObject;
    }

}
