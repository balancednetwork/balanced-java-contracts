
/*
 * Copyright (c) 2022-2022 Balanced.network.
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
package network.balanced.score.lib.utils;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

public class RLPUtils {

    public static String[] readStringArray(ObjectReader r) {
        if( !r.hasNext() ) {
            return new String[]{};
        }

        r.beginList();
        List<String> lst = new ArrayList<>();
        while(r.hasNext()) {
            lst.add(r.readString());
        }
        int size = lst.size();
        String[] arr = new String[size];
        for(int i=0; i < size; i++) {
            arr[i] = lst.get(i);
        }
        r.end();
        return arr;
    }
}
