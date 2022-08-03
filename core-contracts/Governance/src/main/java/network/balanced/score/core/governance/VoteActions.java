package network.balanced.score.core.governance;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import network.balanced.score.lib.structs.StructParser;

import static network.balanced.score.lib.utils.Math.convertToNumber;

import java.util.function.Function;

import score.Address;

public class VoteActions {
     public static void executeAction(JsonArray action) { 
          JsonArray parsedAction = action.asArray();

          Address address = Address.fromString(parsedAction.get(0).asString());
          String method = parsedAction.get(1).asString();
          JsonArray jsonParams = parsedAction.get(2).asArray();
          Object[] params = getConvertedParams(jsonParams);
          GovernanceImpl.call(address, method, params);
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
                    return parse(value, isArray, jsonValue -> convertToNumber(jsonValue));
               case "boolean":
                    return parse(value, isArray, jsonValue -> jsonValue.asBoolean());
               case "bytes":
                    return parse(value, isArray, jsonValue -> convertBytesParam(jsonValue));
               case "DistributionPercentage":
                    return parse(value, isArray, jsonValue -> StructParser.parseDistributionPercentage(jsonValue.asObject()));
               case "Disbursement":
                    return parse(value, isArray, jsonValue -> StructParser.parseDisbursement(jsonValue.asObject()));
               case "PrepDelegations":
                    return parse(value, isArray, jsonValue -> StructParser.parsePrepDelegations(jsonValue.asObject()));
          }

          throw new IllegalArgumentException("Unknown type");
     }    

     private static <T> Object parse(JsonValue value, boolean isArray, Function<JsonValue, T> parser) {
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
}
