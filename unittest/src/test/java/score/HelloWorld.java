/*
 * Copyright 2021 ICONLOOP Inc.
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

package score;

import score.annotation.External;

public class HelloWorld {
    private final String name;
    private final Address echoAddress;

    public HelloWorld(String name, Address echoAddress) {
        this.name = name;
        this.echoAddress = echoAddress;
    }

    @External(readonly=true)
    public String name() {
        return name;
    }

    @External(readonly=true)
    public Address getAddress() {
        return Context.getAddress();
    }

    @External(readonly=true)
    public Address getOwner() {
        return Context.getOwner();
    }

    @External(readonly=true)
    public long getBlockTimestamp() {
        return Context.getBlockTimestamp();
    }

    @External(readonly=true)
    public byte[] computeHash(String algorithm, byte[] data) {
        if ("sha3-256".equals(algorithm)) {
            return Context.hash(algorithm, data);
        }
        return null;
    }

    @External(readonly=true)
    public String castedEcho(String message) {
        return (String) Context.call(echoAddress, "echo", message);
    }

    @External(readonly=true)
    public String typedEcho(String message) {
        return Context.call(String.class, echoAddress, "echo", message);
    }
}
