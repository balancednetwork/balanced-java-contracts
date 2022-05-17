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
import network.balanced.test.Constants;
import network.balanced.test.ResultTimeoutException;
import network.balanced.test.TransactionFailureException;
import network.balanced.test.TransactionHandler;
import network.balanced.test.score.Score;

import java.io.IOException;
import java.math.BigInteger;

import static network.balanced.test.Env.LOG;

public class LoansScore extends Score {
    private static String PYTHON_PATH = "../../testinteg/src/main/java/network/balanced/test/contracts/base/pythonContracts/loans.zip";

    public static LoansScore deploy(TransactionHandler txHandler, Wallet wallet, Address governaceAddress)
            throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Deploy Loans");
        RpcObject params = new RpcObject.Builder()
                .put("_governance", new RpcValue(governaceAddress))
                .build();
        Score score = txHandler.deploy(wallet, PYTHON_PATH, params);
        return new LoansScore(score);
    }

    public LoansScore(Score other) {
        super(other);
    }

    public TransactionResult turnLoansOn(Wallet fromWallet) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "turnLoansOn", params);
    }

    public TransactionResult toggleLoansOn(Wallet fromWallet) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "toggleLoansOn", params);
    }
 
    public TransactionResult setContinuousRewardsDay(Wallet fromWallet, int day) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_day", new RpcValue(BigInteger.valueOf(day)))
            .build();
        return invokeAndWaitResult(fromWallet, "setContinuousRewardsDay", params);
    }

    public TransactionResult addAsset(Wallet fromWallet, Address address, boolean active, boolean collateral) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_token_address", new RpcValue(address))
            .put("_active", new RpcValue(active))
            .put("_collateral", new RpcValue(collateral))
            .build();
        return invokeAndWaitResult(fromWallet, "addAsset", params);
    }

    public TransactionResult toggleAssetActive(Wallet fromWallet, String _symbol) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_symbol", new RpcValue(_symbol))
            .build();
        return invokeAndWaitResult(fromWallet, "toggleAssetActive", params);
    }

    public TransactionResult precompute(Wallet fromWallet, int _snapshot_id, int batch_size) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_snapshot_id", new RpcValue(BigInteger.valueOf(_snapshot_id)))
            .put("batch_size", new RpcValue(BigInteger.valueOf(batch_size)))
            .build();
        return invokeAndWaitResult(fromWallet, "precompute", params);
    }

    public TransactionResult checkForNewDay(Wallet fromWallet) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .build();
        return invokeAndWaitResult(fromWallet, "checkForNewDay", params);
    }

    public TransactionResult checkDistributions(Wallet fromWallet, int _day, boolean _new_day) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_day", new RpcValue(BigInteger.valueOf(_day)))
            .put("_new_day", new RpcValue(_new_day))
            .build();
        return invokeAndWaitResult(fromWallet, "checkDistributions", params);
    }

    public TransactionResult depositAndBorrow(Wallet fromWallet, String _asset, BigInteger _amount, BigInteger _value) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_asset", new RpcValue(_asset))
            .put("_amount", new RpcValue(_amount))
            .build();
        return invokeAndWaitResult(fromWallet, "depositAndBorrow", params, _value, null);
    }

    public TransactionResult retireBadDebt(Wallet fromWallet, String _symbol, BigInteger _value) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_symbol", new RpcValue(_symbol))
            .put("_value", new RpcValue(_value))
            .build();
        return invokeAndWaitResult(fromWallet, "retireBadDebt", params);
    }

    public TransactionResult returnAsset(Wallet fromWallet, String _symbol, BigInteger _value, boolean _repay) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_symbol", new RpcValue(_symbol))
            .put("_value", new RpcValue(_value))
            .put("_repay", new RpcValue(_repay))
            .build();
        return invokeAndWaitResult(fromWallet, "returnAsset", params);
    }

    public TransactionResult withdrawCollateral(Wallet fromWallet, BigInteger _value) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_value", new RpcValue(_value))
            .build();
        return invokeAndWaitResult(fromWallet, "withdrawCollateral", params);
    }

    public TransactionResult liquidate(Wallet fromWallet, Address _owner) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_owner", new RpcValue(_owner))
            .build();
        return invokeAndWaitResult(fromWallet, "liquidate", params);
    }

    public TransactionResult setAdmin(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setAdmin", params);
    }

    public TransactionResult setGovernance(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setGovernance", params);
    }

    public TransactionResult setDex(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setDex", params);
    }

    public TransactionResult setRebalance(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setRebalance", params);
    }

    public TransactionResult setDividends(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setDividends", params);
    }

    public TransactionResult setReserve(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setReserve", params);
    }

    public TransactionResult setRewards(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setRewards", params);
    }

    public TransactionResult setStaking(Wallet fromWallet, Address address) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_address", new RpcValue(address))
            .build();
        return invokeAndWaitResult(fromWallet, "setStaking", params);
    }

    public TransactionResult setMiningRatio(Wallet fromWallet, BigInteger ratio) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_ratio", new RpcValue(ratio))
            .build();
        return invokeAndWaitResult(fromWallet, "setMiningRatio", params);
    }

    public TransactionResult setLockingRatio(Wallet fromWallet, BigInteger ratio) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_ratio", new RpcValue(ratio))
            .build();
        return invokeAndWaitResult(fromWallet, "setLockingRatio", params);
    }

    public TransactionResult setLiquidationRatio(Wallet fromWallet, BigInteger ratio) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_ratio", new RpcValue(ratio))
            .build();
        return invokeAndWaitResult(fromWallet, "setLiquidationRatio", params);
    }

    public TransactionResult setOriginationFee(Wallet fromWallet, BigInteger fee) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_fee", new RpcValue(fee))
            .build();
        return invokeAndWaitResult(fromWallet, "setOriginationFee", params);
    }

    public TransactionResult setRedemptionFee(Wallet fromWallet, BigInteger fee) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_fee", new RpcValue(fee))
            .build();
        return invokeAndWaitResult(fromWallet, "setRedemptionFee", params);
    }

    public TransactionResult setRetirementBonus(Wallet fromWallet, BigInteger bonus) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_points", new RpcValue(bonus))
            .build();
        return invokeAndWaitResult(fromWallet, "setRetirementBonus", params);
    }

    public TransactionResult setLiquidationReward(Wallet fromWallet, BigInteger reward) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_points", new RpcValue(reward))
            .build();
        return invokeAndWaitResult(fromWallet, "setLiquidationReward", params);
    }

    public TransactionResult setNewLoanMinimum(Wallet fromWallet, BigInteger min) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_minimum", new RpcValue(min))
            .build();
        return invokeAndWaitResult(fromWallet, "setNewLoanMinimum", params);
    }

    public TransactionResult setMinMiningDebt(Wallet fromWallet, BigInteger min) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_minimum", new RpcValue(min))
            .build();
        return invokeAndWaitResult(fromWallet, "setMinMiningDebt", params);
    }

    public TransactionResult setTimeOffset(Wallet fromWallet, long deltaTime) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_delta_time", new RpcValue(BigInteger.valueOf(deltaTime)))
            .build();
        return invokeAndWaitResult(fromWallet, "setTimeOffset", params);
    }

    public TransactionResult setMaxRetirePercent(Wallet fromWallet, BigInteger max) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_value", new RpcValue(max))
            .build();
        return invokeAndWaitResult(fromWallet, "setMaxRetirePercent", params);
    }

    public TransactionResult setRedeemBatchSize(Wallet fromWallet, int size) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_value", new RpcValue(BigInteger.valueOf(size)))
            .build();
        return invokeAndWaitResult(fromWallet, "setRedeemBatchSize", params);
    }


    public BigInteger getPositionIndex(Address positionAddress) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_owner", new RpcValue(positionAddress))
            .build();
        return call("getAccountPositions", params).asObject().getItem("pos_id").asInteger();
    }

    public BigInteger getDay() throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .build();
        return call("getDay", params).asValue().asInteger();
    }
}