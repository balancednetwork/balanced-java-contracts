/*
 * Copyright 2018 ICON Foundation
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

package network.balanced.test.contracts.base;

import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.icx.transport.jsonrpc.RpcArray;
import network.balanced.test.Constants;
import network.balanced.test.ResultTimeoutException;
import network.balanced.test.TransactionFailureException;
import network.balanced.test.TransactionHandler;
import network.balanced.test.score.Score;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.HashMap;

import static network.balanced.test.Env.LOG;

public class Governance extends Score {
    protected static final String PYTHON_PATH = "../../testinteg/src/main/java/network/balanced/test/contracts/base/pythonContracts/governance.zip";
    public LoansScore loans;
    public DexScore dex;
    public StakingScore staking;
    public RewardsScore rewards;
    public ReserveScore reserve;
    public DividendsScore dividends;
    public DaoFundScore daofund;
    public DummyOracle oracle;
    public SICXScore sicx;
    public BnUSDScore bnUSD;
    public BalnScore baln;
    public BwtScore bwt;
    public RebalancingScore rebalancing;
    public RouterScore router;
    public FeeHandlerScore feehandler;

    public static Governance deploy(TransactionHandler txHandler, Wallet wallet)
        throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Deploy Governance");
        RpcObject params = new RpcObject.Builder()
            .build();
        Score score = txHandler.deploy(wallet, PYTHON_PATH, params);
        return new Governance(score);
    }


    public Governance(Score other) {
        super(other);
    }

    public void setupBalanced(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        deployContracts(txHandler, adminWallet);
        setAddresses(adminWallet);
        staking.setSicxAddress(adminWallet, sicx.getAddress());
        configureBalanced(adminWallet);
        launchBalanced(adminWallet);
        staking.toggleStakingOn(adminWallet);
        delegate(adminWallet);
        createBnusdMarket(adminWallet, BigInteger.valueOf(210).multiply(BigInteger.TEN.pow(18)));
    }

    public void deployContracts(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
       deployLoans(txHandler, adminWallet);
       deployDex(txHandler, adminWallet);
       deployStaking(txHandler, adminWallet);
       deployRewards(txHandler, adminWallet);
       deployReserve(txHandler, adminWallet);
       deployDividends(txHandler, adminWallet);
       deployDaoFund(txHandler, adminWallet);
       deployOracle(txHandler, adminWallet);
       deploySICX(txHandler, adminWallet);
       deployBnUSD(txHandler, adminWallet);
       deployBaln(txHandler, adminWallet);
       deployBwt(txHandler, adminWallet);
       deployRebalancing(txHandler, adminWallet);
       deployRouter(txHandler, adminWallet);
       deployFeeHandler(txHandler, adminWallet);
    }

    protected void deployLoans(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        loans = LoansScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployDex(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        dex = DexScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployStaking(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        staking = StakingScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployRewards(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        rewards = RewardsScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployReserve(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        reserve = ReserveScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployDividends(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        dividends = DividendsScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployDaoFund(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        daofund = DaoFundScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployOracle(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        oracle = DummyOracle.deploy(txHandler, adminWallet);
    }

    protected void deploySICX(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        sicx = SICXScore.deploy(txHandler, adminWallet, staking.getAddress());
    }

    protected void deployBnUSD(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        bnUSD = BnUSDScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployBaln(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        baln = BalnScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployBwt(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        bwt = BwtScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployRebalancing(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        rebalancing = RebalancingScore.deploy(txHandler, adminWallet, getAddress());
    }

    protected void deployRouter(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        router = RouterScore.deploy(txHandler, adminWallet,getAddress());
    }

    protected void deployFeeHandler(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        feehandler = FeeHandlerScore.deploy(txHandler, adminWallet,getAddress());
    }

    public TransactionResult setAddresses(Wallet fromWallet) throws IOException, ResultTimeoutException {
        RpcObject contracts = new RpcObject.Builder()
            .put("loans", new RpcValue(loans.getAddress()))
            .put("dex", new RpcValue(dex.getAddress()))
            .put("staking", new RpcValue(staking.getAddress()))
            .put("rewards", new RpcValue(rewards.getAddress()))
            .put("reserve", new RpcValue(reserve.getAddress()))
            .put("dividends", new RpcValue(dividends.getAddress()))
            .put("daofund", new RpcValue(daofund.getAddress()))
            .put("oracle", new RpcValue(oracle.getAddress()))
            .put("sicx", new RpcValue(sicx.getAddress()))
            .put("bnUSD", new RpcValue(bnUSD.getAddress()))
            .put("baln", new RpcValue(baln.getAddress()))
            .put("bwt", new RpcValue(bwt.getAddress()))
            .put("rebalancing", new RpcValue(rebalancing.getAddress()))
            .put("router", new RpcValue(router.getAddress()))
            .put("feehandler", new RpcValue(feehandler.getAddress()))
        .build();

        RpcObject params = new RpcObject.Builder()
            .put("_addresses", contracts)
            .build();
        return invokeAndWaitResult(fromWallet, "setAddresses", params);
    }

    public TransactionResult configureBalanced(Wallet fromWallet) throws IOException, ResultTimeoutException {
        LOG.info("Configure Balanced");

        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "configureBalanced", params);
    }

    public TransactionResult launchBalanced(Wallet fromWallet) throws IOException, ResultTimeoutException {
        LOG.info("Launch Balanced");
        
        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "launchBalanced", params);
    }

    public TransactionResult delegate(Wallet fromWallet) throws IOException, ResultTimeoutException {
        LOG.info("Delegate");
        RpcObject delegation = new RpcObject.Builder()
            .put("_address", new RpcValue(fromWallet.getAddress()))
            .put("_votes_in_per", new RpcValue(BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18))))
            .build();
        RpcArray delegations = new RpcArray.Builder()
            .add(delegation)
            .build();
        RpcObject params = new RpcObject.Builder()
            .put("_delegations", delegations)
            .build();

        return invokeAndWaitResult(fromWallet, "delegate", params);
    }

    public TransactionResult createBnusdMarket(Wallet fromWallet, BigInteger value) throws IOException, ResultTimeoutException {
        LOG.info("Create Bnusd Market");
        
        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "createBnusdMarket", params, value, null);
    }

    public TransactionResult setLoansRebalance(Wallet fromWallet, Address rebalance) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(rebalance))
            .build();
        return invokeAndWaitResult(fromWallet, "setLoansRebalance", params);
    }

    public TransactionResult setLoansDex(Wallet fromWallet, Address dex) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(dex))
            .build();
        return invokeAndWaitResult(fromWallet, "setLoansDex", params);
    }
    
    public LoansScore getLoans() {
        return loans;
    }

    public DexScore getDex() {
        return dex;
    }

    public StakingScore getStaking() {
        return staking;
    }

    public RewardsScore getRewards() {
        return rewards;
    }

    public ReserveScore getReserve() {
        return reserve;
    }

    public DividendsScore getDividends() {
        return dividends;
    }

    public DaoFundScore getDaofund() {
        return daofund;
    }

    public DummyOracle getOracle () {
        return oracle;
    }

    public SICXScore getSicx() {
        return sicx;
    }

    public BnUSDScore getBnUSD() {
        return bnUSD;
    }

    public BalnScore getBaln() {
        return baln;
    }

    public BwtScore getBwt() {
        return bwt;
    }

    public RouterScore getRouter() {
        return router;
    }

    public RebalancingScore getRebalance() {
        return rebalancing;
    }
    
    public FeeHandlerScore getFeehandler() {
        return feehandler;
    }

}