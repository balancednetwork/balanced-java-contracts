package network.balanced.score.core.governance.utils;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import network.balanced.score.core.governance.GovernanceImpl;

import static network.balanced.score.lib.utils.Math.convertToNumber;

import java.util.Map;
import java.util.function.Function;

import score.Address;
import score.Context;
import scorex.util.HashMap;

public class ArbitraryCallManager {
     public static final String METHOD = "method";
     public static final String ADDRESS = "address";
     public static final String PARAMS = "parameters";

     public static void executeTransactions(String transactions) {
          JsonArray actionsList = Json.parse(transactions).asArray();
          for (int i = 0; i < actionsList.size(); i++) {
              JsonObject transaction = actionsList.get(i).asObject();
              executeTransaction(transaction);
          }
      }

     public static void executeTransaction(JsonObject transaction) { 
          Address address = Address.fromString(transaction.get(ADDRESS).asString());
          String method = transaction.get(METHOD).asString();
          JsonArray jsonParams = transaction.get(PARAMS).asArray();
          Object[] params = getConvertedParameters(jsonParams);
          GovernanceImpl.call(address, method, params);
     }

     public static Object[] getConvertedParameters(JsonArray params) {
          Object[] convertedParameters = new Object[params.size()];
          int i = 0;
          for (JsonValue param : params) {
               convertedParameters[i++] = getConvertedParameter(param);
          }

          return convertedParameters;
     }
     
     public static Object getConvertedParameter(JsonValue param) {
          JsonObject member = param.asObject();
          String type = member.getString("type", null);
          JsonValue paramValue = member.get("value");
          if (type.endsWith("[]")) {
               return convertParam(type.substring(0, type.length() - 2), paramValue, true);
          }

          return convertParam(type, paramValue, false);
     }

     private static Object convertParam(String type, JsonValue value, boolean isArray){
          switch (type) {
               case "Address":
                    return parse(value, isArray, jsonValue -> Address.fromString(jsonValue.asString()));
               case "String":
                    return parse(value, isArray, jsonValue -> jsonValue.asString());
               case "int":
               case "BigInteger":
               case "Long":
               case "Short":
                    return parse(value, isArray, jsonValue -> convertToNumber(jsonValue));
               case "boolean":
               case "Boolean":
                    return parse(value, isArray, jsonValue -> jsonValue.asBoolean());
               case "Struct":
                    return parse(value, isArray, jsonValue -> parseStruct(jsonValue.asObject()));
               case "bytes":
               case "byte[]":
                    return parse(value, isArray, jsonValue -> convertBytesParam(jsonValue));
          }

          throw new IllegalArgumentException("Unknown type");
     }    

     private static Object parse(JsonValue value, boolean isArray, Function<JsonValue, ?> parser) {
          if (isArray) {
               return parseArray(value, parser);
          }

          return parser.apply(value);
     }

     private static Object parseArray(JsonValue value, Function<JsonValue, ?> parser) {
          JsonArray array = value.asArray();
          Object[] convertedArray =  new Object[array.size()];
          int i = 0;
          for (JsonValue param : array) {
               convertedArray[i++] = parser.apply(param);
          }

          return convertedArray;
     }

     private static Object convertBytesParam(JsonValue value) {
          String hex = value.asString();
          Context.require(hex.length() % 2 == 0, "Illegal bytes format");

          if (hex.startsWith("0x")) {
               hex = hex.substring(2);
          }

          int len = hex.length() / 2;
          byte[] bytes = new byte[len];
          for (int i = 0; i < len; i++) {
               int j = i * 2;
               bytes[i] = (byte) Integer.parseInt(hex.substring(j, j + 2), 16);
          }

          return bytes;
     }

     private static Object parseStruct(JsonObject jsonStruct) {
          Map<String, Object> struct = new HashMap<String, Object>();
          for (JsonObject.Member member : jsonStruct) {
               String name = member.getName();
               JsonObject jsonObject = member.getValue().asObject();
               String type = jsonObject.getString("type", null);
               JsonValue jsonValue = jsonObject.get("value");

               if (type.endsWith("[]")) {
                    struct.put(name, convertParam(type.substring(0, type.length() - 2), jsonValue.asArray(), true));
               } else {
                    struct.put(name, convertParam(type, jsonValue, false));
               }
          }
         
          return struct;
     }
}
