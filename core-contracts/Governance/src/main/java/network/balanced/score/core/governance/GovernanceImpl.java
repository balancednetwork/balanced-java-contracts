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

package network.balanced.score.core.governance;

import score.Address;
import score.Context;
import score.UserRevertException;
import score.VarDB;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import score.annotation.EventLog;
import scorex.util.ArrayList;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

import static network.balanced.score.core.governance.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Check.*;
import static network.balanced.score.lib.utils.Math.pow;

import network.balanced.score.core.governance.interfaces.*;


import network.balanced.score.lib.structs.BalancedAddresses;
import network.balanced.score.lib.structs.Disbursement;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.structs.PrepDelegations;

public class GovernanceImpl {
    public final VarDB<BigInteger> launchDay = Context.newVarDB(LAUNCH_DAY, BigInteger.class);
    public final VarDB<BigInteger> launchTime = Context.newVarDB(LAUNCH_TIME, BigInteger.class);
    public final VarDB<Boolean> launched = Context.newVarDB(LAUNCHED, Boolean.class);
    public final VarDB<Address> rebalancing = Context.newVarDB(REBALANCING, Address.class);
    public final VarDB<BigInteger> timeOffset = Context.newVarDB(TIME_OFFSET, BigInteger.class);
    public final VarDB<BigInteger> voteDuration = Context.newVarDB(VOTE_DURATION, BigInteger.class);
    public final VarDB<BigInteger> balnVoteDefinitionCriterion = Context.newVarDB(MIN_BALN, BigInteger.class);
    public final VarDB<BigInteger> bnusdVoteDefinitionFee = Context.newVarDB(DEFINITION_FEE, BigInteger.class);
    public final VarDB<BigInteger> quorum = Context.newVarDB(QUORUM, BigInteger.class);

    public GovernanceImpl() {
        if (launched.getOrDefault(null) == null) {
            launched.set(false);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced Governance";
    }

    @External(readonly = true)
    public BigInteger getDay() {
        BigInteger blockTime = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(timeOffset.getOrDefault(BigInteger.ZERO));
        return blockTime.divide(U_SECONDS_DAY);
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotersCount(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
            "for_voters", proposal.forVotersCount.getOrDefault(BigInteger.ZERO),
            "against_voters", proposal.againstVotersCount.getOrDefault(BigInteger.ZERO)
        );
    }

    @External(readonly = true)
    public Address getContractAddress(String contract) {
        return Addresses.get(contract);
    }

    @External
    public void setVoteDuration(BigInteger duration) {
        onlyOwner();
        _setVoteDuration(duration);
    }

    @External(readonly = true)
    public BigInteger getVoteDuration() {
        return voteDuration.get();
    }

    @External
    public void setContinuousRewardsDay(BigInteger _day) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        DividendsScoreInterface dividends = new DividendsScoreInterface(Addresses.get("dividends"));

        loans.setContinuousRewardsDay(_day);
        dex.setContinuousRewardsDay(_day);
        rewards.setContinuousRewardsDay(_day);
        dividends.setContinuousRewardsDay(_day);
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.setFeeProcessingInterval(_interval);
    }

    @External
    public void deleteRoute(Address _fromToken, Address _toToken) {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.deleteRoute(_fromToken, _toToken);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.setAcceptedDividendTokens(_tokens);
    }

    @External
    public void setRoute(Address _fromToken, Address _toToken, Address[] _path) {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.setRoute(_fromToken, _toToken, _path);
    }

    @External
    public void setQuorum(BigInteger quorum) {
        onlyOwner();
        _setQuorum(quorum);
    }

    @External(readonly = true)
    public BigInteger getQuorum() {
        return quorum.get();
    }

    @External
    public void setVoteDefinitionFee(BigInteger fee) {
        onlyOwner();
        _setVoteDefinitionFee(fee);
    }

    @External(readonly = true)
    public BigInteger getVoteDefinitionFee() {
        return bnusdVoteDefinitionFee.get();
    }

    @External
    public void setBalnVoteDefinitionCriterion(BigInteger percentage) {
        onlyOwner();
        _setBalnVoteDefinitionCriterion(percentage);
    }

    @External(readonly = true)
    public BigInteger getBalnVoteDefinitionCriterion() {
        return balnVoteDefinitionCriterion.get();
    }

    @External
    public void cancelVote(BigInteger vote_index) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require(vote_index.compareTo(BigInteger.ONE) >= 0, "There is no proposal with index " + vote_index);
        Context.require(vote_index.compareTo(proposal.proposalsCount.get()) <= 0, "There is no proposal with index " + vote_index);
        Context.require(proposal.status.get() == ProposalStatus.STATUS[ProposalStatus.ACTIVE], "Proposal can be cancelled only from active status.");
        Context.require(Context.getCaller() == proposal.proposer.get() || 
                        Context.getCaller() == Context.getOwner(), 
                        "Only owner or proposer may call this method.");

        Context.require(proposal.startSnapshot.get().compareTo(getDay()) <= 0 &&
                        Context.getCaller() == Context.getOwner(), 
                        "Only owner can cancel a vote that has started.");

        refundVoteDefinitionFee(proposal);
        proposal.active.set(false);
        proposal.status.set(ProposalStatus.STATUS[ProposalStatus.CANCELLED]);
    }

    @External
    public void defineVote(String name, String description, BigInteger vote_start, BigInteger snapshot, @Optional String actions) {
        Context.require(description.length() <= 500, "Description must be less than or equal to 500 characters.");
        Context.require(vote_start.compareTo(getDay()) > 0, "Vote cannot start at or before the current day.");
        Context.require(getDay().compareTo(snapshot) <= 0 && 
                        snapshot.compareTo(vote_start) < 0, 
                        "The reference snapshot must be in the range: [current_day (" + getDay() + "), " +
                        "start_day - 1 (" + vote_start.subtract(BigInteger.ONE)+ ")].");

        actions = optionalDefault(actions, "[]");
        BigInteger voteIndex = ProposalDB.getProposalId(name);
        Context.require(voteIndex.equals(BigInteger.ZERO), "Poll name " + name + " has already been used.");
        Context.require(checkBalnVoteCriterion(Context.getCaller()), "User needs at least " + balnVoteDefinitionCriterion.get().divide(BigInteger.valueOf(100)) + "% of total baln supply staked to define a vote.");
        verifyActions(actions);
  
        BnUSDScoreInterface bnUSD = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        bnUSD.govTransfer(Context.getCaller(), Addresses.get("daofund"), bnusdVoteDefinitionFee.get(), new byte[0]);
        

        ProposalDB.createProposal(
                name,
                description,
                Context.getCaller(),
                quorum.get().multiply(EXA).divide(BigInteger.valueOf(100)),
                MAJORITY,
                snapshot,
                vote_start,
                vote_start.add(voteDuration.get()),
                actions,
                bnusdVoteDefinitionFee.get()
        );
    }

    @External
    public void tryExecuteActions(String actions) {
        try {
            JsonArray actionsParsed = Json.parse(actions).asArray();
            Context.require(actionsParsed.size() <= maxActions(), TAG + ": Only " + maxActions() + " actions are allowed");
            executeVoteActions(actions);
            throw new SuccsesfulVoteExecution();
        } catch (SuccsesfulVoteExecution e) {
            throw new UserRevertException();
        }
    }

    @External(readonly = true)
    public int maxActions() {
        return 5;
    }

    @External(readonly = true)
    public BigInteger getProposalCount() {
        return ProposalDB.getProposalCount();
    }

    @External(readonly = true)
    public List<Object> getProposals(@Optional BigInteger batch_size, @Optional BigInteger offset) {
        batch_size = optionalDefault(batch_size, BigInteger.valueOf(20));
        offset = optionalDefault(offset, BigInteger.ONE);

        BigInteger start = BigInteger.ONE.max(offset);
        BigInteger end =  batch_size.add(start).subtract(BigInteger.ONE).min(getProposalCount());
        List<Object> proposals = new ArrayList<>();
        for (BigInteger i = start; i.compareTo(end) <= 0; i = i.add(BigInteger.ONE)) {
            proposals.add(checkVote(i));
        }

        return proposals;
    }

    @External
    public void castVote(BigInteger vote_index, boolean vote) {
        ProposalDB proposal = new ProposalDB(vote_index);
        Context.require((vote_index.compareTo(BigInteger.ZERO)  > 0 &&
                        getDay().compareTo(proposal.startSnapshot.getOrDefault(BigInteger.ZERO)) >= 0 &&
                        getDay().compareTo(proposal.endSnapshot.getOrDefault(BigInteger.ZERO)) < 0) && 
                        proposal.active.getOrDefault(false),
                        TAG + " :This is not an active poll.");

        Address from = Context.getCaller();
        BigInteger snapshot = proposal.voteSnapshot.get();

        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        BigInteger userStaked = baln.stakedBalanceOfAt(from, snapshot);
        BigInteger totalVote = userStaked;

        Context.require(!totalVote.equals(BigInteger.ZERO), TAG + "Balanced tokens need to be staked to cast the vote.");

        BigInteger userForVotes = proposal.forVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger userAgainstVotes = proposal.againstVotesOfUser.getOrDefault(from, BigInteger.ZERO);
        BigInteger totalForVotes = proposal.totalForVotes.getOrDefault(BigInteger.ZERO);
        BigInteger totalAgainstVotes = proposal.totalAgainstVotes.getOrDefault(BigInteger.ZERO);
        BigInteger totalForVotersCount = proposal.forVotersCount.getOrDefault(BigInteger.ZERO);
        BigInteger totalForAgainstVotersCount = proposal.againstVotersCount.getOrDefault(BigInteger.ZERO);
        BigInteger totalFor;
        BigInteger totalAgainst;
        boolean isFirstTimeVote = userForVotes.signum() == 0 && userAgainstVotes.signum() == 0;

        if (vote) {
            proposal.forVotesOfUser.set(from, totalVote);
            proposal.againstVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath

            totalFor = totalForVotes.add(totalVote).subtract(userForVotes);
            totalAgainst = totalAgainstVotes.subtract(userAgainstVotes);
            if (isFirstTimeVote) {
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            } else if (userAgainstVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.subtract(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.add(BigInteger.ONE));
            }
        } else {
            proposal.againstVotesOfUser.set(from, totalVote);
            proposal.forVotesOfUser.set(from, BigInteger.ZERO);
            //TODO use safemath
            totalFor = totalForVotes.subtract(userForVotes);
            totalAgainst = totalAgainstVotes.add(totalVote).subtract(userAgainstVotes);

            if (isFirstTimeVote) {
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
            } else if (userForVotes.compareTo(BigInteger.ZERO) > 0) {
                //TODO use safemath
                proposal.againstVotersCount.set(totalForAgainstVotersCount.add(BigInteger.ONE));
                proposal.forVotersCount.set(totalForVotersCount.subtract(BigInteger.ONE));
            }
        }

        proposal.totalForVotes.set(totalFor);
        proposal.totalAgainstVotes.set(totalAgainst);

        VoteCast(proposal.name.get(), vote, from, totalVote, totalFor, totalAgainst);
    }

    @External(readonly = true)
    public BigInteger totalBaln(BigInteger _day) {
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        BigInteger stakedBaln = baln.totalStakedBalanceOfAt(_day);
        
        //TODD should be remove before continiuos?
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        BigInteger balnFromBnusdPool = dex.totalBalnAt(BALNBNUSD_ID, _day, false);
        BigInteger balnFromSICXPool = dex.totalBalnAt(BALNSICX_ID, _day, false);

        return stakedBaln.add(balnFromBnusdPool).add(balnFromSICXPool);
    }

    @External
    public void evaluateVote(BigInteger vote_index) {
        Context.require(vote_index.compareTo(BigInteger.ZERO) > 0 && 
                        vote_index.compareTo(ProposalDB.getProposalCount()) <= 0,
                        TAG + ": There is no proposal with index " + vote_index);

        ProposalDB proposal = new ProposalDB(vote_index);
        BigInteger endSnap = proposal.endSnapshot.get();
        String actions = proposal.actions.get();
        BigInteger majority = proposal.majority.get();

        Context.require(getDay().compareTo(endSnap) >= 0, TAG + ": Voting period has not ended.");
        Context.require(proposal.active.get(), TAG + ": This proposal is not active");
       
       
        Map<String, Object> result = checkVote(vote_index);
        proposal.active.set(false);
   
        BigInteger forVotes = (BigInteger) result.get("for");
        BigInteger againstVotes = (BigInteger) result.get("against");
        if (forVotes.add(againstVotes).compareTo(proposal.quorum.get()) < 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.NO_QUORUM]);
            return;
        }

        //TODO SafeMath
        BigInteger percentageFor = EXA.subtract(majority).multiply(forVotes);
        BigInteger percentageAgainst = majority.multiply(againstVotes);
        if (percentageFor.compareTo(percentageAgainst) <= 0) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.DEFEATED]);
            return;
        } else if (actions.equals("[]")){
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.SUCCEEDED]);
            _refundVoteDefinitionFee(proposal);
            return;
        }

        try {
            executeVoteActions(proposal.actions.get());
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.EXECUTED]);
        } catch (Exception e) {
            proposal.status.set(ProposalStatus.STATUS[ProposalStatus.FAILED_EXECUTION]);
        }

        _refundVoteDefinitionFee(proposal);    
    }

    @External(readonly = true)
    public BigInteger getVoteIndex(String _name) {
        return ProposalDB.getProposalId(_name);
    }

    @External(readonly = true)
    public Map<String, Object> checkVote(BigInteger _vote_index) {
        if (_vote_index.compareTo( BigInteger.ONE) < 0 ||
            _vote_index.compareTo(ProposalDB.getProposalCount()) > 0) {
            return Map.of();
        }

        ProposalDB proposal = new ProposalDB(_vote_index);
        BigInteger totalBaln = totalBaln(proposal.voteSnapshot.get());

        BigInteger nrForVotes = BigInteger.ZERO;
        BigInteger nrAgainstVotes = BigInteger.ZERO;
        if (!totalBaln.equals(BigInteger.ZERO)) {
            nrForVotes = proposal.totalForVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBaln);
            nrAgainstVotes = proposal.totalAgainstVotes.getOrDefault(BigInteger.ZERO).multiply(EXA).divide(totalBaln);
        }

        return Map.ofEntries(
            entry("id", _vote_index),
            entry("name", proposal.name.get()),
            entry("proposer", proposal.proposer.get()),
            entry("description", proposal.description.get()),
            entry("majority", proposal.majority.get()),
            entry("status", proposal.status.get()),
            entry("vote snapshot", proposal.voteSnapshot.get()),
            entry("start day", proposal.startSnapshot.get()),
            entry("end day", proposal.endSnapshot.get()),
            entry("actions", proposal.actions.get()),
            entry("quorum", proposal.quorum.get()),
            entry("for", nrForVotes),
            entry("against", nrAgainstVotes),
            entry("for_voter_count", proposal.forVotersCount.get()),
            entry("against_voter_count", proposal.againstVotersCount.get()),
            entry("fee_refund_status", proposal.feeRefunded.get())

        );
    }

    @External(readonly = true)
    public Map<String, BigInteger> getVotesOfUser(BigInteger vote_index, Address user ) {
        ProposalDB proposal = new ProposalDB(vote_index);
        return Map.of(
            "for", proposal.forVotesOfUser.get(user),
            "against", proposal.againstVotesOfUser.get(user)
        );
    }

    @External(readonly = true)
    public BigInteger myVotingWeight(Address _address, BigInteger _day) {
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        return baln.stakedBalanceOfAt(_address, _day);
    }

    @External
    public void configureBalanced() {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        for(Map<String, Object> asset : ASSETS) {
            loans.addAsset(
                Addresses.get((String) asset.get("address")),
                (boolean) asset.get("active"),
                (boolean) asset.get("collateral")
            );
        }
    }

    @External
    public void launchBalanced() {
        onlyOwner();
        if (launched.get() ) {
            return;
        }

        launched.set(true);
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));

        BigInteger offset = DAY_ZERO.add(launchDay.getOrDefault(BigInteger.ZERO));
        BigInteger day = BigInteger.valueOf(Context.getBlockTimestamp()).subtract(DAY_START).divide(U_SECONDS_DAY).subtract(offset);
        launchDay.set(day);
        launchTime.set(BigInteger.valueOf(Context.getBlockTimestamp()));

        BigInteger timeDelta = DAY_START.add(U_SECONDS_DAY).multiply(DAY_ZERO.add(launchDay.get()).subtract(BigInteger.ONE));
        loans.setTimeOffset(timeDelta);
        dex.setTimeOffset(timeDelta);
        rewards.setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
           rewards.addNewDataSource(source.get("name"), Addresses.get(source.get("address")));
        }

        rewards.updateBalTokenDistPercentage(RECIPIENTS);
        
        balanceToggleStakingEnabled();
        loans.turnLoansOn();
        dex.turnDexOn();
    }

    @External
    @Payable
    public void createBnusdMarket() {
        onlyOwner();

        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = Addresses.get("dex");
        Address sICXAddress = Addresses.get("sicx");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address stakingAddress = Addresses.get("staking");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        BnUSDScoreInterface bnsud = new BnUSDScoreInterface(bnUSDAddress);
        SicxScoreInterface sicx = new SicxScoreInterface(sICXAddress);
        StakingScoreInterface staking = new StakingScoreInterface(stakingAddress);
        LoansScoreInterface loans = new LoansScoreInterface(loansAddress);
        DexScoreInterface dex = new DexScoreInterface(dexAddress);
        StakedLpScoreInterface stakedLp = new StakedLpScoreInterface(stakedLpAddress);
        RewardsScoreInterface rewards = new RewardsScoreInterface(rewardsAddress);

        BigInteger price = bnsud.priceInLoop();
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        staking.stakeICX(value.divide(BigInteger.valueOf(7)));
        loans.depositAndBorrow(Context.getBalance(Context.getAddress()), "bnUSD", amount, null, BigInteger.ZERO);

        BigInteger bnUSDValue = bnsud.balanceOf(Context.getAddress());
        BigInteger sICXValue = sicx.balanceOf(Context.getAddress());

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        bnsud.transfer(dexAddress, bnUSDValue, depositData.toString().getBytes());
        sicx.transfer(dexAddress, sICXValue, depositData.toString().getBytes());

        dex.add(sICXAddress, bnUSDAddress, sICXValue, bnUSDValue, false);
        String name = "sICX/bnUSD";
        BigInteger pid = dex.getPoolId(sICXAddress, bnUSDAddress);
        dex.setMarketName(pid, name);

        rewards.addNewDataSource(name, dexAddress);
        stakedLp.addPool(pid);
        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(225).multiply(pow(BigInteger.TEN,15))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15)))
        };

        rewards.updateBalTokenDistPercentage(recipients);
    }

    @External
    public void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        onlyOwner();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address bnUSDAddress = Addresses.get("bnUSD");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");
        Address loansAddress = Addresses.get("loans");

        AssetScoreInterface bnsud = new AssetScoreInterface(bnUSDAddress);
        AssetScoreInterface baln = new AssetScoreInterface(balnAddress);
        LoansScoreInterface loans = new LoansScoreInterface(loansAddress);
        DexScoreInterface dex = new DexScoreInterface(dexAddress);
        StakedLpScoreInterface stakedLp = new StakedLpScoreInterface(stakedLpAddress);
        RewardsScoreInterface rewards = new RewardsScoreInterface(rewardsAddress);

        rewards.claimRewards();
        loans.depositAndBorrow("bnUSD", _bnUSD_amount, null, BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        bnsud.transfer(dexAddress, _bnUSD_amount, depositData.toString().getBytes());
        baln.transfer(dexAddress, _baln_amount, depositData.toString().getBytes());

        dex.add(balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount, false);
        String name = "BALN/bnUSD";
        BigInteger pid = dex.getPoolId(balnAddress, bnUSDAddress);
        dex.setMarketName(pid, name);

        rewards.addNewDataSource(name, dexAddress);
        stakedLp.addPool(pid);

        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15))),
            createDistributionPercentage("BALN/bnUSD",  BigInteger.valueOf(175).multiply(pow(BigInteger.TEN,15)))
        };

        rewards.updateBalTokenDistPercentage(recipients);
    }

    @External
    public void createBalnSicxMarket(BigInteger  _sicx_amount, BigInteger _baln_amount) {
        onlyOwner();

        Address dexAddress = Addresses.get("dex");
        Address balnAddress = Addresses.get("baln");
        Address sICXAddress = Addresses.get("sicx");
        Address stakedLpAddress = Addresses.get("stakedLp");
        Address rewardsAddress = Addresses.get("rewards");

        AssetScoreInterface sicx = new AssetScoreInterface(sICXAddress);
        AssetScoreInterface baln = new AssetScoreInterface(balnAddress);
        DexScoreInterface dex = new DexScoreInterface(dexAddress);
        StakedLpScoreInterface stakedLp = new StakedLpScoreInterface(stakedLpAddress);
        RewardsScoreInterface rewards = new RewardsScoreInterface(rewardsAddress);

        rewards.claimRewards();

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        sicx.transfer(dexAddress, _sicx_amount, depositData.toString().getBytes());
        baln.transfer(dexAddress, _baln_amount, depositData.toString().getBytes());

        dex.add(balnAddress, sICXAddress, _baln_amount, _sicx_amount, false);
        String name = "BALN/sICX";
        BigInteger pid = dex.getPoolId(balnAddress, sICXAddress);
        dex.setMarketName(pid, name);

        rewards.addNewDataSource(name, dex._address());
        stakedLp.addPool(pid);
 
        DistributionPercentage[] recipients = new DistributionPercentage[] {
            createDistributionPercentage("Loans",  BigInteger.valueOf(25).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/ICX",  BigInteger.TEN.multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Worker Tokens",  BigInteger.valueOf(20).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("Reserve Fund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("DAOfund",  BigInteger.valueOf(5).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("sICX/bnUSD",  BigInteger.valueOf(15).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("BALN/bnUSD",  BigInteger.valueOf(15).multiply(pow(BigInteger.TEN,16))),
            createDistributionPercentage("BALN/sICX",  BigInteger.valueOf(10).multiply(pow(BigInteger.TEN,16)))
        };

       rewards.updateBalTokenDistPercentage(recipients);
    }

    @External
    public void rebalancingSetBnusd(Address _address) {
        onlyOwner();
        RebalancingScoreInterface rebalance = new RebalancingScoreInterface(rebalancing.get());
        rebalance.setBnusd(_address);
    }

    @External
    public void rebalancingSetSicx(Address _address) {
        onlyOwner();
        RebalancingScoreInterface rebalance = new RebalancingScoreInterface(rebalancing.get());
        rebalance.setSicx(_address);
    }

    @External
    public void rebalancingSetDex(Address _address) {
        onlyOwner();
        RebalancingScoreInterface rebalance = new RebalancingScoreInterface(rebalancing.get());
        rebalance.setDex(_address);
    }

    @External
    public void rebalancingSetLoans(Address _address) {
        onlyOwner();
        RebalancingScoreInterface rebalance = new RebalancingScoreInterface(rebalancing.get());
        rebalance.setLoans(_address);
    }

    @External
    public void setLoansRebalance(Address _address) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setRebalancing(_address);
    }

    @External
    public void setLoansDex(Address _address) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setDex(_address);
    }

    @External
    public void setRebalancing(Address _address) {
        onlyOwner();
        rebalancing.set(_address);
    }

    @External
    public void setRebalancingThreshold(BigInteger _value) {
        onlyOwner();
        _setRebalancingThreshold(_value);
    }

    @External
    public void setAddresses(BalancedAddresses _addresses) {
        onlyOwner();
        Addresses.setAddresses(_addresses);
    }

    @External(readonly = true)
    public Map<String, Address>  getAddresses() {
        return Addresses.getAddresses();
    }

    @External
    public void setAdmins() {
        onlyOwner();
        Addresses.setAdmins();
    }

    @External
    public void setContractAddresses() {
        onlyOwner();
        Addresses.setContractAddresses();

    }

    @External
    public void toggleBalancedOn() {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.toggleLoansOn();
    }

    @External(readonly = true)
    public BigInteger getLaunchDay() {
        return launchDay.get();
    }

    @External(readonly = true)
    public BigInteger getLaunchTime() {
        return launchTime.get();
    }

    @External
    public void addAsset(Address _token_address, boolean _active, boolean _collateral) {
        onlyOwner();
        Address loansAddress = Addresses.get("loans");
        LoansScoreInterface loans = new LoansScoreInterface(loansAddress);
        loans.addAsset(_token_address, _active, _collateral);
        Context.call(_token_address, "setAdmin", loansAddress);
    }

    @External
    public void toggleAssetActive(String _symbol) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.toggleAssetActive(_symbol);
    }

    @External
    public void addNewDataSource(String _data_source_name, String _contract_address) {
        onlyOwner();
        _addNewDataSource(_data_source_name, _contract_address);
    }

    @External
    public void removeDataSource(String _data_source_name) {
        onlyOwner();
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        rewards.removeDataSource(_data_source_name);
    }

    @External
    public void updateBalTokenDistPercentage(DistributionPercentage[] _recipient_list) {
        onlyOwner();
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        Object test = (Object)_recipient_list;
        rewards.updateBalTokenDistPercentage((DistributionPercentage[])test);
    }

    @External
    public void bonusDist(Address[] _addresses, BigInteger[] _amounts) {
        onlyOwner();
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        rewards.bonusDist(_addresses, _amounts);
    }

    @External
    public void setDay(BigInteger _day) {
        onlyOwner();
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        rewards.setDay(_day);
    }

    @External
    public void dexPermit(BigInteger _id, boolean _permission) {
        onlyOwner();
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        dex.permit(_id, _permission);
    }

    @External
    public void dexAddQuoteCoin(Address _address) {
        onlyOwner();
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        dex.addQuoteCoin(_address);
    }

    @External
    public void setMarketName(BigInteger _id, String _name) {
        onlyOwner();
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        dex.setMarketName(_id, _name);
    }

    @External
    public void delegate(PrepDelegations[] _delegations) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.delegate(_delegations);
    }

    @External
    public void balwAdminTransfer(Address _from , Address _to , BigInteger _value, byte[] _data) {
        onlyOwner();
        BwtScoreInterface bwt = new BwtScoreInterface(Addresses.get("bwt"));
        bwt.adminTransfer(_from, _to, _value, _data);
    }

    @External
    public void setbnUSD(Address _address) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setBnusd(_address);        
    }

    @External
    public void setDividends(Address _score) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setDividends(_score);
    }

    @External
    public void balanceSetDex(Address _address) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setDex(_address);
    }

    @External
    public void balanceSetOracleName(String _name) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setOracleName(_name);
    }

    @External
    public void balanceSetMinInterval(BigInteger _interval) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setMinInterval(_interval);
    }

    @External
    public void balanceToggleStakingEnabled() {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.toggleStakingEnabled();
    }

    @External
    public void balanceSetMinimumStake(BigInteger _amount) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setMinimumStake(_amount);
    }

    @External
    public void balanceSetUnstakingPeriod(BigInteger _time) {
        onlyOwner();
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));
        baln.setUnstakingPeriod(_time);
    }

    @External
    public void addAcceptedTokens(String _token) {
        onlyOwner();
        _addAcceptedTokens(_token);
    }

    @External
    public void setAssetOracle(String _symbol, Address _address) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        Map<String, Address> assetAddresses = loans.getAssetTokens();
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setOracle", _address);
    }

    @External
    public void setAssetOracleName(String _symbol, String _name) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        Map<String, Address> assetAddresses = loans.getAssetTokens();       
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setOracleName", _name);
    }

    @External
    public void setAssetMinInterval(String _symbol, BigInteger _interval) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        Map<String, Address> assetAddresses = loans.getAssetTokens();
        Context.require(assetAddresses.containsKey(_symbol), TAG + ": " + _symbol + " is not a supported asset in Balanced.");

        Address token = assetAddresses.get(_symbol);
        Context.call(token, "setMinInterval", _interval);
    }

    @External
    public void bnUSDSetOracle(Address _address) {
        onlyOwner();
        BnUSDScoreInterface bnUSD = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        bnUSD.setOracle(_address);
    }

    @External
    public void bnUSDSetOracleName(String _name) {
        onlyOwner();
        BnUSDScoreInterface bnUSD = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        bnUSD.setOracleName(_name);
    }

    @External
    public void bnUSDSetMinInterval(BigInteger _interval) {
        onlyOwner();
        BnUSDScoreInterface bnUSD = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        bnUSD.setMinInterval(_interval);
    }

    @External
    public void addUsersToActiveAddresses(BigInteger _poolId, Address[] _addressList) {
        onlyOwner();
        DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
        dex.addLpAddresses(_poolId, _addressList);
    }

    @External
    public void setRedemptionFee(BigInteger _fee) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setRedemptionFee(_fee);
    }

    @External
    public void setMaxRetirePercent(BigInteger _value) {
        onlyOwner();
        _setMaxRetirePercent(_value);
    }

    @External
    public void setRedeemBatchSize(BigInteger _value) {
        onlyOwner();
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setRedeemBatchSize(_value);
    }

    @External
    public void addPoolOnStakedLp(BigInteger _id) {
        onlyOwner();
        StakedLpScoreInterface stakedLp = new StakedLpScoreInterface(Addresses.get("stakedLp"));
        stakedLp.addPool(_id);
    }

    @External
    public void setAddressesOnContract(String _contract) {
        onlyOwner();
        Addresses.setAddress(_contract);
    }

    @External
    public void setRouter(Address _router) {
        onlyOwner();
        Addresses.router.set(_router);
    }

    @External
    public void enable_fee_handler() {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.enable();
    }

    @External
    public void disable_fee_handler() {
        onlyOwner();
        FeehandlerScoreInterface feehandler = new FeehandlerScoreInterface(Addresses.get("feehandler"));
        feehandler.disable();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {}

    @Payable
    public void fallback() {}

    private void refundVoteDefinitionFee(ProposalDB proposal) {
        if (proposal.feeRefunded.getOrDefault(false)) {
            return;
        }
        
        BnUSDScoreInterface bnusd = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        bnusd.govTransfer(Addresses.get("daofund"), proposal.proposer.get(), proposal.fee.get(), new byte[0]);
        proposal.feeRefunded.set(true);
    }

    private boolean checkBalnVoteCriterion(Address address) {
        BalnScoreInterface baln = new BalnScoreInterface(Addresses.get("baln"));

        BigInteger balnTotal = baln.totalSupply();
        BigInteger userStaked = baln.stakedBalanceOf(address);
        BigInteger limit = balnVoteDefinitionCriterion.get();
        BigInteger userPercentage = POINTS.multiply(userStaked).divide(balnTotal);
        return userPercentage.compareTo(limit) >= 0;
    }

    private void _refundVoteDefinitionFee(ProposalDB proposal) {
        BnUSDScoreInterface bnusd = new BnUSDScoreInterface(Addresses.get("bnUSD"));
        
        Address daoFund = Addresses.get("daofund");
        bnusd.govTransfer(daoFund, Context.getCaller(), proposal.fee.get(), new byte[0]);
        proposal.feeRefunded.set(true);
    }

    private void verifyActions(String actions) {
        try {
            Context.call(Context.getAddress(), "tryExecuteActions", actions);
        } catch (UserRevertException e) {
            // success
        }
    }

    private void executeVoteActions(String actions){
        JsonArray actionsList = Json.parse(actions).asArray();
        for (int i = 0; i < actionsList.size(); i++){
            JsonValue action = actionsList.get(i);
            JsonArray parsedAction = action.asArray();

            String method = parsedAction.get(0).asString();
            JsonObject parameters = parsedAction.get(1).asObject();

            VoteActions.execute(this, method, parameters);
        }
    }

    public void daoDisburse(String _recipient,  Disbursement[] _amounts ) {
        Context.require(_amounts.length <=  3, "Cannot disburse more than 3 assets at a time.");
        Address recipient = Address.fromString(_recipient);
        DaofundScoreInterface dao = new DaofundScoreInterface(Addresses.get("daofund"));
        dao.disburse(recipient, _amounts);
    }

    public void enableDividends() {
        DividendsScoreInterface dividends = new DividendsScoreInterface(Addresses.get("dividends"));
        dividends.setDistributionActivationStatus(true);
    }

    public void setMiningRatio(BigInteger _value) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setMiningRatio(_value);
    }

    public void setLockingRatio(BigInteger _value) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setLockingRatio(_value);
    }

    public void setOriginationFee(BigInteger _fee) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setOriginationFee(_fee);
    }

    public void setLiquidationRatio(BigInteger _ratio) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setLiquidationRatio(_ratio);
    }

    public void setRetirementBonus(BigInteger _points) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setRetirementBonus(_points);
    }

    public void setLiquidationReward(BigInteger _points) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setLiquidationReward(_points);
    }

    public void setDividendsCategoryPercentage(DistributionPercentage[] _dist_list) {
        DividendsScoreInterface dividends = new DividendsScoreInterface(Addresses.get("dividends"));
        dividends.setDividendsCategoryPercentage(_dist_list);
    }

    // Unreacable in current version
    // public void setPoolLpFee(BigInteger _value) {
    //     DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
    //     dex.setPoolLpFee(_value);
    // }

    // public void setPoolBalnFee(BigInteger _value) {
    //     DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
    //     dex.setPoolBalnFee(_value);       
    // }

    // public void setIcxConversionFee(BigInteger _value) {
    //     DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
    //     dex.setIcxConversionFee(_value);
    // }

    // public void setIcxBalnFee(BigInteger _value) {
    //      DexScoreInterface dex = new DexScoreInterface(Addresses.get("dex"));
    //      dex.setIcxBalnFee(_value);
    // }

    public void _setVoteDuration(BigInteger duration) {
        voteDuration.set(duration);
    }

    public void _setVoteDefinitionFee(BigInteger fee) {
        bnusdVoteDefinitionFee.set(fee);
    }

    public void _setQuorum(BigInteger quorum) {
        Context.require(quorum.compareTo(BigInteger.ZERO) > 0,"Quorum must be between 0 and 100.");
        Context.require(quorum.compareTo(BigInteger.valueOf(100)) < 0,"Quorum must be between 0 and 100.");

        this.quorum.set(quorum);
    }

    public void _setBalnVoteDefinitionCriterion(BigInteger percentage) {
        Context.require(percentage.compareTo(BigInteger.ZERO) >= 0, "Basis point must be between 0 and 10000.");
        Context.require(percentage.compareTo(BigInteger.valueOf(10000)) <= 0, "Basis point must be between 0 and 10000.");
   
        balnVoteDefinitionCriterion.set(percentage);
    }

    public void _addNewDataSource(String _data_source_name, String _contract_address) {
        RewardsScoreInterface rewards = new RewardsScoreInterface(Addresses.get("rewards"));
        rewards.addNewDataSource(_data_source_name, Address.fromString(_contract_address));
    }

    public void _addAcceptedTokens(String _token) {
        Address token = Address.fromString(_token);
        DividendsScoreInterface dividends = new DividendsScoreInterface(Addresses.get("dividends"));
        dividends.addAcceptedTokens(token);
    }

    public void _setMaxRetirePercent(BigInteger _value) {
        LoansScoreInterface loans = new LoansScoreInterface(Addresses.get("loans"));
        loans.setMaxRetirePercent(_value);
    }


    public void _setRebalancingThreshold(BigInteger _value) {
        RebalancingScoreInterface rebalance = new RebalancingScoreInterface(rebalancing.get());
        rebalance.setPriceDiffThreshold(_value);
    }
    
    @EventLog(indexed = 2)
    void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for, BigInteger total_against){}
}
