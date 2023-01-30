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
package network.balanced.score.lib.interfaces;

import foundation.icon.score.client.ScoreClient;
import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.interfaces.base.Fallback;
import network.balanced.score.lib.interfaces.base.Name;
import network.balanced.score.lib.interfaces.base.TokenFallback;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@ScoreClient
@ScoreInterface
public interface Governance extends
        Name,
        TokenFallback,
        Fallback {

    @External(readonly = true)
    BigInteger getDay();

    @External(readonly = true)
    Map<String, BigInteger> getVotersCount(BigInteger vote_index);

    @External
    void changeScoreOwner(Address score, Address newOwner);

    @External(readonly = true)
    Address getContractAddress(String contract);

    @External
    void setVoteDurationLimits(BigInteger min, BigInteger max);

    @External(readonly = true)
    BigInteger getMinVoteDuration();

    @External(readonly = true)
    BigInteger getMaxVoteDuration();

    @External
    void setTimeOffset(BigInteger offset);

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External
    void setQuorum(BigInteger quorum);

    @External(readonly = true)
    BigInteger getQuorum();

    @External
    void setVoteDefinitionFee(BigInteger fee);

    @External(readonly = true)
    BigInteger getVoteDefinitionFee();

    @External
    void setBalnVoteDefinitionCriterion(BigInteger percentage);

    @External(readonly = true)
    BigInteger getBalnVoteDefinitionCriterion();

    @External
    void cancelVote(BigInteger vote_index);

    @External
    void defineVote(String name, String description, BigInteger vote_start, BigInteger duration, String forumLink,
                    String transactions);

    @External
    void tryExecuteTransactions(String transactions);

    @External
    void addExternalContract(String name, Address address);

    @External
    void deployTo(Address targetContract, byte[] contractData, String deploymentParams);

    @External
    void deploy(byte[] contractData, String deploymentParams);

    @External
    void execute(String transactions);

    @External
    void disable();

    @External
    void enable();

    @External
    void blacklist(String address);

    @External
    void removeBlacklist(String address);

    @External(readonly = true)
    boolean isBlacklisted(String address);

    @External(readonly = true)
    Map<String, Boolean> getBlacklist();

    @External
    void addAuthorizedCallerShutdown(Address address);

    @External
    void removeAuthorizedCallerShutdown(Address address);

    @External
    void setShutdownPrivilegeTimeLock(BigInteger days);

    @External(readonly = true)
    BigInteger getShutdownPrivilegeTimeLock();

    @External(readonly = true)
    Map<String, BigInteger> getAuthorizedCallersShutdown();

    @External(readonly = true)
    BigInteger getProposalCount();

    @External(readonly = true)
    List<Object> getProposals(@Optional BigInteger batch_size, @Optional BigInteger offset);

    @External
    void castVote(BigInteger vote_index, boolean vote);

    @External
    void evaluateVote(BigInteger vote_index);

    @External(readonly = true)
    BigInteger getVoteIndex(String _name);

    @External(readonly = true)
    Map<String, Object> checkVote(BigInteger _vote_index);

    @External(readonly = true)
    Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user);

    @External(readonly = true)
    BigInteger myVotingWeight(Address _address, BigInteger _day);

    @External
    void configureBalanced();

    @External
    void launchBalanced();

    @External
    @Payable
    void createBnusdMarket();

    @External
    void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount);

    @External
    void createBalnSicxMarket(BigInteger _sicx_amount, BigInteger _baln_amount);

    @External(readonly = true)
    Map<String, Address> getAddresses();

    @External(readonly = true)
    Address getAddress(String name);

    @External
    void setAdmins();

    @External
    void setAdmin(Address contractAddress, Address admin);

    @External
    void setContractAddresses();

    @External(readonly = true)
    BigInteger getLaunchDay();

    @External(readonly = true)
    BigInteger getLaunchTime();

    @External
    void addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
                       BigInteger _liquidationRatio, BigInteger _debtCeiling);

    @External
    void delegate(String contract, PrepDelegations[] _delegations);

    @External
    void balwAdminTransfer(Address _from, Address _to, BigInteger _value, byte[] _data);

    @External
    void setAddressesOnContract(String _contract);
}