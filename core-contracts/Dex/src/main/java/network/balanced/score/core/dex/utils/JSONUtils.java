/*
 * Copyright (c) 2024 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.dex.utils;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

public class JSONUtils {
  public static byte[] method (String method) {
    return ("{\"method\": \"" + method + "\"}").getBytes();
  }
  
  public static byte[] method (String method, JsonObject params) {
    JsonObject data = Json.object()
        .add("method", method)
        .add("params", params);

    byte[] dataBytes = data.toString().getBytes();

    return dataBytes;
  }

  public static JsonObject parse (byte[] _data) {
    return Json.parse(new String(_data)).asObject();
  }

  public static JsonObject parse (String _data) {
    return Json.parse(_data).asObject();
  }
}
