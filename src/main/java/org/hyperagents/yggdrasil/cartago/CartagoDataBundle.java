package org.hyperagents.yggdrasil.cartago;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.JacksonCodec;

/**
 * A class for serializing and deserializing CArtAgO datatypes to JSON. This is used for
 * sending action parameters via the event bus.
 *
 * @author Andrei Ciortea
 *
 */
public final class CartagoDataBundle {

  public static String toJson(final List<Object> params) {
    return Json.encode(CartagoDataBundle.objectListToTypedList(params));
  }

  public static Object[] fromJson(final String representation) {
    return CartagoDataBundle.typedListToObjectList(JacksonCodec.decodeValue(representation, new TypeReference<>() {})).toArray();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> typedListToObjectList(final List<List<Object>> typedParams) {
    return typedParams.stream()
                      .map(param -> {
                        final var type = (String) param.get(0);
                        if (type.equals(String.class.getCanonicalName())) {
                          return param.get(1);
                        } else if (type.equals(Integer.class.getCanonicalName())) {
                          return Integer.valueOf((String) param.get(1));
                        } else if (type.equals(Double.class.getCanonicalName())) {
                          return Double.valueOf((String) param.get(1));
                        } else if (type.equals(Boolean.class.getCanonicalName())) {
                          return Boolean.valueOf((String) param.get(1));
                        } else if (type.equals(List.class.getCanonicalName())) {
                          return CartagoDataBundle.typedListToObjectList((List<List<Object>>) param.get(1)).toArray();
                        } else {
                          return null;
                        }
                      })
                      .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private static List<List<Object>> objectListToTypedList(final List<Object> params) {
    return params.stream()
                 .map(param -> {
                   final var typedParam = new ArrayList<>();
                   if (param instanceof List<?>) {
                     typedParam.add(List.class.getCanonicalName());
                     typedParam.add(CartagoDataBundle.objectListToTypedList((List<Object>) param));
                   } else {
                     typedParam.add(param.getClass().getCanonicalName());
                     typedParam.add(String.valueOf(param));
                   }
                   return typedParam;
                 })
                 .collect(Collectors.toList());
  }
}
