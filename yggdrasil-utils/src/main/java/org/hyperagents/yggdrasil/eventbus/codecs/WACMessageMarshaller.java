package org.hyperagents.yggdrasil.eventbus.codecs;

import java.lang.reflect.Type;

import org.hyperagents.yggdrasil.eventbus.messages.WACMessage;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class WACMessageMarshaller implements JsonSerializer<WACMessage>, JsonDeserializer<WACMessage> {

    @Override
    public WACMessage deserialize(JsonElement json, Type type, JsonDeserializationContext context)
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
                    case GET_WAC_RESOURCE -> new WACMessage.GetWACResource(
                        jsonObject.get(MessageFields.ACCESSED_RESOURCE_URI.getName()).getAsString()
                    );
                    case AUTHORIZE_ACCESS -> new WACMessage.AuthorizeAccess(
                        jsonObject.get(MessageFields.ACCESSED_RESOURCE_URI.getName()).getAsString(),
                        jsonObject.get(MessageFields.AGENT_URI.getName()).getAsString(),
                        jsonObject.get(MessageFields.ACCESS_TYPE.getName()).getAsString()
                    );
                    default -> throw new JsonParseException("The request method is not valid");
                };
    }

    @Override
    public JsonElement serialize(WACMessage wacMessage, Type type, JsonSerializationContext jsonContext) {
        final var jsonObject = new JsonObject();
        
        switch(wacMessage) {
            case WACMessage.GetWACResource getWACResource -> {
                jsonObject.addProperty(MessageFields.REQUEST_METHOD.getName(), MessageRequestMethods.GET_WAC_RESOURCE.getName());
                jsonObject.addProperty(MessageFields.ACCESSED_RESOURCE_URI.getName(), getWACResource.accessedResourceURI());
            }
            case WACMessage.AuthorizeAccess authorizeAccess -> {
                jsonObject.addProperty(MessageFields.REQUEST_METHOD.getName(), MessageRequestMethods.AUTHORIZE_ACCESS.getName());
                jsonObject.addProperty(MessageFields.ACCESSED_RESOURCE_URI.getName(), authorizeAccess.accessedResourceURI());
                jsonObject.addProperty(MessageFields.AGENT_URI.getName(), authorizeAccess.agentURI());
                jsonObject.addProperty(MessageFields.ACCESS_TYPE.getName(), authorizeAccess.accessType());
            }
        }

        return jsonObject;
    }
    
}
