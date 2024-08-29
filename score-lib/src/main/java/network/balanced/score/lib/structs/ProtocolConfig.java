/*
 * Copyright 2022 ICON Foundation
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

package network.balanced.score.lib.structs;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;
import scorex.util.ArrayList;

import java.util.List;

public class ProtocolConfig {
    public String[] sources;
    public String[] destinations;
    public static final String sourcesKey = "sources";
    public static final String destinationsKey = "destinations";

    public ProtocolConfig(String[] sources, String[] destinations) {
        this.sources = sources;
        this.destinations = destinations;
    }

    public ProtocolConfig() {
        this.sources = new String[0];
        this.destinations = new String[0];
    }

    public static void writeObject(ObjectWriter w, ProtocolConfig m) {
        w.beginList(2);

        w.beginList(m.sources.length);
        for(String src : m.sources) {
            w.write(src);
        }
        w.end();

        w.beginList(m.destinations.length);
        for(String dst : m.destinations) {
            w.write(dst);
        }
        w.end();
        w.end();
    }

    public static ProtocolConfig readObject(ObjectReader r) {
        r.beginList();
        ProtocolConfig m = new ProtocolConfig(
            readProtocols(r),
            readProtocols(r)
        );
        r.end();
        return m;
    }

    private static String[] readProtocols(ObjectReader r) {
        if(!r.hasNext() ) {
            return new String[]{};
        }

        r.beginList();
        List<String> protocolsList = new ArrayList<>();
        while(r.hasNext()) {
            protocolsList.add(r.readString());
        }
        int size = protocolsList.size();
        String[] protocols = new String[size];
        for(int i=0; i < size; i++) {
            protocols[i] = protocolsList.get(i);
        }
        r.end();
        return protocols;
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        ProtocolConfig.writeObject(writer, this);
        return writer.toByteArray();
    }

    public static ProtocolConfig fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return readObject(reader);
    }
}
