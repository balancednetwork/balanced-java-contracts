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

package network.balanced.gradle.plugin.utils;

import java.math.BigInteger;

public enum Network {
    SEJONG("https://sejong.net.solidwallet.io/api/v3", BigInteger.valueOf(83L)),
    BERLIN("https://berlin.net.solidwallet.io/api/v3", BigInteger.valueOf(7L)),
    LISBON("https://lisbon.net.solidwallet.io/api/v3", BigInteger.valueOf(2L)),
    MAINNET("https://ctz.solidwallet.io/api/v3", BigInteger.valueOf(3L)),
    LOCAL("http://localhost:9082/api/v3", BigInteger.valueOf(3L));

    private final String url;
    private final BigInteger nid;

    Network(String url, BigInteger nid) {
        this.url = url;
        this.nid = nid;
    }

    public String getUrl() {
        return url;
    }

    public BigInteger getNid() {
        return nid;
    }

    public static Network getNetwork(String network) {
        switch (network.toLowerCase()) {
            case "sejong":
                return SEJONG;
            case "mainnet":
                return MAINNET;
            case "berlin":
                return BERLIN;
            case "lisbon":
                return LISBON;
            default:
                return LOCAL;
        }
    }
}