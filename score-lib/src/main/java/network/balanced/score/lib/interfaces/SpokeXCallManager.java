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

package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.annotations.XCall;
import network.balanced.score.lib.interfaces.base.Version;
import network.balanced.score.lib.interfaces.base.Name;
import score.Address;
import score.annotation.External;

import java.util.Map;

@ScoreClient
@ScoreInterface
public interface SpokeXCallManager extends Version, Name {
    @XCall
    void execute(String from, String transactions);

    @XCall
    void configureProtocols(String from, String[] sources, String[] destinations);

    @External
    void proposeRemoval(String address);

    @External(readonly = true)
    String getProposedRemoval();

    @External(readonly = true)
    Map<String, String[]>  getProtocols();

    @External(readonly = true)
    void verifyProtocols(String[] protocols);

    @External
    void setAdmin(Address address);

    @External(readonly = true)
    Address getAdmin();

    @External
    void setXCall(Address address);

    @External(readonly = true)
    Address getXCall();
}
