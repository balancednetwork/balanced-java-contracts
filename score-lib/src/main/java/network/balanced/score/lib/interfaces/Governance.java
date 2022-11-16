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
import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;
import score.Address;
import score.annotation.External;
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

    @External(readonly = true)
    Address getContractAddress(String contract);

    @External
    void setVoteDurationLimits(BigInteger min, BigInteger max);

    @External
    void setTimeOffset(BigInteger offset);

    @External(readonly = true)
    BigInteger getTimeOffset();

    @External(readonly = true)
    BigInteger getVoteDuration();

    @External
    void setFeeProcessingInterval(BigInteger _interval);

    @External
    void deleteRoute(Address _fromToken, Address _toToken);

    @External
    void setAcceptedDividendTokens(Address[] _tokens);

    @External
    void setRoute(Address _fromToken, Address _toToken, Address[] _path);

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
                    String actions);

    @External
    void tryExecuteActions(String actions);

    @External(readonly = true)
    int maxActions();

    @External(readonly = true)
    BigInteger getProposalCount();

    @External(readonly = true)
    List<Object> getProposals(int batch_size, int offset);

    @External
    void castVote(BigInteger vote_index, boolean vote);

    @External(readonly = true)
    BigInteger totalBaln(BigInteger _day);

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

    @External
    void rebalancingSetBnusd(Address _address);

    @External
    void rebalancingSetSicx(Address _address);

    @External
    void rebalancingSetDex(Address _address);

    @External
    void rebalancingSetLoans(Address _address);

    @External
    void setLoansRebalance(Address _address);

    @External
    void setLoansDex(Address _address);

    @External
    void setRebalancing(Address _address);

    @External
    void setRebalancingThreshold(BigInteger _value);

    @External
    void setAddresses(BalancedAddresses _addresses);

    @External(readonly = true)
    Map<String, Address> getAddresses();

    @External
    void setAdmins();

    @External
    void setAdmin(Address contractAddress, Address admin);

    @External
    void setContractAddresses();

    @External
    void toggleBalancedOn();

    @External(readonly = true)
    BigInteger getLaunchDay();

    @External(readonly = true)
    BigInteger getLaunchTime();

    @External
    void addCollateral(Address _token_address, boolean _active, String _peg, BigInteger _lockingRatio,
                       BigInteger _liquidationRatio, BigInteger _debtCeiling);

    @External
    void addDexPricedCollateral(Address _token_address, boolean _active, BigInteger _lockingRatio,
                                BigInteger _liquidationRatio, BigInteger _debtCeiling);

    @External
    void setDebtCeiling(String _symbol, BigInteger _debtCeiling);

    @External
    void toggleAssetActive(String _symbol);

    @External
    void setPeg(String _symbol, String _peg);

    @External
    void addDexPricedAsset(String _symbol, BigInteger _limit);

    @External
    void removeDexPricedAsset(String _symbol);

    void addNewDataSource(String _data_source_name, String _contract_address);

    @External
    void addStakedLpDataSource(String _name, BigInteger _poolId, int _sourceType);

    @External
    void removeDataSource(String _data_source_name);

    @External
    void setPlatformDistPercentage(String name, BigInteger percentage);

    @External
    void setFixedSourcePercentage(String name, BigInteger percentage);

    @External
    void setVotable(String name, boolean votable);

    @External
    void addType(String name);

    @External
    void changeTypeWeight(int typeId, BigInteger weight);

    @External
    void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list);

    @External
    void bonusDist(Address[] _addresses, BigInteger[] _amounts);

    @External
    void setDay(BigInteger _day);

    @External
    void dexPermit(BigInteger _id, boolean _permission);

    @External
    void dexAddQuoteCoin(Address _address);

    @External
    void setMarketName(BigInteger _id, String _name);

    @External
    void delegate(PrepDelegations[] _delegations);

    @External
    void balwAdminTransfer(Address _from, Address _to, BigInteger _value, byte[] _data);

    @External
    void setbnUSD(Address _address);

    @External
    void setDividends(Address _score);

    @External
    void balanceSetDex(Address _address);

    @External
    void balanceSetOracleName(String _name);

    @External
    void balanceSetMinInterval(BigInteger _interval);

    @External
    void balanceToggleStakingEnabled();

    @External
    void balanceSetMinimumStake(BigInteger _amount);

    @External
    void balanceSetUnstakingPeriod(BigInteger _time);

    @External
    void addAcceptedTokens(String _token);

    @External
    void setAssetOracle(String _symbol, Address _address);

    @External
    void setAssetOracleName(String _symbol, String _name);

    @External
    void setAssetMinInterval(String _symbol, BigInteger _interval);

    @External
    void bnUSDSetOracle(Address _address);

    @External
    void bnUSDSetOracleName(String _name);

    @External
    void bnUSDSetMinInterval(BigInteger _interval);

    @External
    void addUsersToActiveAddresses(BigInteger _poolId, Address[] _addressList);

    @External
    void setRedemptionFee(BigInteger _fee);

    @External
    void setMaxRetirePercent(BigInteger _value);

    @External
    void setRedeemBatchSize(BigInteger _value);

    @External
    void setAddressesOnContract(String _contract);

    @External
    void setRouter(Address _router);

    @External
    void enable_fee_handler();

    @External
    void disable_fee_handler();
}