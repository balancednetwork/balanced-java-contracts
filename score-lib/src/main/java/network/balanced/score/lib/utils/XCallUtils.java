/*
 * Copyright (c) 2022-2023 Balanced.network.
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

import score.Context;
import score.VarDB;
import foundation.icon.xcall.NetworkAddress;
import foundation.icon.xcall.messages.Message;
import foundation.icon.xcall.messages.PersistentMessage;
import foundation.icon.xcall.messages.XCallEnvelope;
import network.balanced.score.lib.structs.ProtocolConfig;

import java.math.BigInteger;
import java.util.Map;
public class XCallUtils {
    private static final String TAG = "XCallUtils";
    private static final VarDB<String> nativeNid = Context.newVarDB(TAG + "NativeNetworkId", String.class);

    public static String getNativeNid() {
        String nid = nativeNid.get();
        if (nid == null) {
            nid = Context.call(String.class, BalancedAddressManager.getXCall(), "getNetworkId");
        }

        return nid;
    }

    public static void verifyXCallProtocols(String _from, String[] protocols) {
        NetworkAddress from = NetworkAddress.valueOf(_from);
        Context.call(BalancedAddressManager.getXCallManager(), "verifyProtocols", from.net(), protocols);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String[]> getProtocols(String nid) {
        return (Map<String, String[]>) Context.call(BalancedAddressManager.getXCallManager(), "getProtocols", nid);
    }

    public static ProtocolConfig getProtocolsRaw(String nid) {
        return ProtocolConfig.fromBytes(Context.call(byte[].class, BalancedAddressManager.getXCallManager(), "getProtocolsRaw", nid));
    }

    public static void sendCall(BigInteger fee, NetworkAddress to, byte[] data, byte[] rollback) {
        Map<String, String[]> protocols = getProtocols(to.net());
        Context.call(fee, BalancedAddressManager.getXCall(), "sendCallMessage", to.toString(), data, rollback, protocols.get(ProtocolConfig.sourcesKey), protocols.get(ProtocolConfig.destinationsKey));
    }

    public static void sendPersistentCall(BigInteger fee, NetworkAddress to, byte[] data) {
        ProtocolConfig protocols = getProtocolsRaw(to.net());
        byte[] msg = createPersistentMessage(data, protocols.sources, protocols.destinations);
        Context.call(fee, BalancedAddressManager.getXCall(), "sendCall", to.toString(), msg);
    }

    public static byte[] createPersistentMessage(byte[] data, String[] sources, String[] destinations) {
        Message msg = new PersistentMessage(data);
        return new XCallEnvelope(msg, sources, destinations).toBytes();
    }

}