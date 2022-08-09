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
          Object[] params = getConvertedParams(jsonParams);
          GovernanceImpl.call(address, method, params);
     }

     public static Object[] getConvertedParams(String params) {
          JsonArray paramsList = Json.parse(params).asArray();
          return getConvertedParams(paramsList);
     }

     private static Object[] getConvertedParams(JsonArray params) {
          Object[] convertedParameters = new Object[params.size()];
          int i = 0;
          for (JsonValue param : params) {
              JsonObject member = param.asObject();
               String type = member.getString("type", null);
               JsonValue paramValue = member.get("value");
               if (type.endsWith("[]")) {
                    convertedParameters[i++] = convertParam(type.substring(0, type.length() - 2), paramValue.asArray(), true);
               } else {
                    convertedParameters[i++] = convertParam(type, paramValue, false);
               }
          }

          return convertedParameters;
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
                    return parse(value, isArray, jsonValue -> convertToNumber(jsonValue));
               case "boolean":
               case "Boolean":
                    return parse(value, isArray, jsonValue -> jsonValue.asBoolean());
               case "Struct":
                    return parse(value, isArray, jsonValue -> parseStruct(jsonValue.asObject()));
               case "bytes":
                    return parse(value, isArray, jsonValue -> convertBytesParam(jsonValue));
          }

          throw new IllegalArgumentException("Unknown type");
     }    

     private static Object parse(JsonValue value, boolean isArray, Function<JsonValue, ?> parser) {
          if (!isArray) {
               return parser.apply(value);
          }

          JsonArray array = value.asArray();
          Object[] convertedArray =  new Object[array.size()];
          int i = 0;
          for (JsonValue param : array) {
               convertedArray[i++] = parser.apply(param);
          }

          return convertedArray;
     }

     private static Object convertBytesParam(JsonValue value) {
          String stringValue = value.asString();
          if (stringValue.startsWith("0x") && (stringValue.length() % 2 == 0)) {
               String hex = stringValue.substring(2);
               int len = hex.length() / 2;
               byte[] bytes = new byte[len];
               for (int i = 0; i < len; i++) {
                    int j = i * 2;
                    bytes[i] = (byte) Integer.parseInt(hex.substring(j, j + 2), 16);
               }

               return (Object) bytes;
          }

          throw new IllegalArgumentException("Illegal bytes format"); 
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
