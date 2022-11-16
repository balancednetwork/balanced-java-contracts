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

package network.balanced.score.lib.interfaces.base;

import score.Address;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.lib.structs.Point;
import network.balanced.score.lib.structs.VotedSlope;

public interface RewardsVoting {
    @External
    void setVotable(String name, boolean votable);

    @External
    void addDataSource(String name, int sourceType, BigInteger weight);

    @External
    void addType(String name);

    @External
    void changeTypeWeight(int typeId, BigInteger weight);

    @External
    void checkpoint();

    @External
    void checkpointSource(String name);

    @External
    BigInteger updateRelativeSourceWeight(String name, BigInteger time);

    @External(readonly = true)
    BigInteger getRelativeSourceWeight(String name, @Optional BigInteger time);

    @External
    void voteForSource(String name, BigInteger userWeight);

    @External(readonly = true)
    boolean isVotable(String name);

    @External(readonly = true)
    Point getSourceWeight(String sourceName, @Optional BigInteger time);

    @External(readonly = true)
    VotedSlope getUserSlope(Address user, String source);

    @External(readonly = true)
    BigInteger getLastUserVote(Address user, String source);

    @External(readonly = true)
    BigInteger getCurrentTypeWeight(int typeId);

    @External(readonly = true)
    BigInteger getTotalWeight();

    @External(readonly = true)
    Point getWeightsSumPerType(int typeId);

    @External(readonly = true)
    int getTypeId(String name);

    @External(readonly = true)
    String getTypeName(int typeId);

    @External(readonly = true)
    int getSourceType(String sourceName);

    @External(readonly = true)
    Map<String, Map<String, BigInteger>> getUserVoteData(Address user);
}