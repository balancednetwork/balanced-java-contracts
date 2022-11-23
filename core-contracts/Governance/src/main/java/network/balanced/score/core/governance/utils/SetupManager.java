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

package network.balanced.score.core.governance.utils;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.utils.Names;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.governance.GovernanceImpl.*;
import static network.balanced.score.core.governance.utils.GovernanceConstants.*;
import static network.balanced.score.lib.utils.Math.pow;

public class SetupManager {

    public static void configureBalanced() {
        for (Map<String, Object> asset : ASSETS) {
            Address tokenAddress = ContractManager.get((String) asset.get("address"));
            call(
                    ContractManager.getAddress(Names.LOANS),
                    "addAsset",
                    tokenAddress,
                    asset.get("active"),
                    asset.get("collateral")
            );
            call(ContractManager.getAddress(Names.DIVIDENDS), "addAcceptedTokens", tokenAddress);
            call(ContractManager.getAddress(Names.DAOFUND), "addAcceptedToken", tokenAddress);
        }

        Address[] acceptedFeeTokens = new Address[]{
                ContractManager.getAddress(Names.SICX),
                ContractManager.getAddress(Names.BNUSD),
                ContractManager.getAddress(Names.BALN)
        };

        call(ContractManager.getAddress(Names.FEEHANDLER), "setAcceptedDividendTokens", (Object) acceptedFeeTokens);


        Address rewardsAddress = ContractManager.getAddress(Names.REWARDS);
        call(rewardsAddress, "addDataProvider", ContractManager.getAddress(Names.STAKEDLP));
        call(rewardsAddress, "addDataProvider", ContractManager.getAddress(Names.DEX));
        call(rewardsAddress, "addDataProvider", ContractManager.getAddress(Names.LOANS));

        call(ContractManager.getAddress(Names.BALN), "setMinter", rewardsAddress);
        call(ContractManager.getAddress(Names.BNUSD), "setMinter", ContractManager.getAddress(Names.LOANS));
        call(ContractManager.getAddress(Names.BNUSD), "setMinter2", ContractManager.getAddress(Names.STABILITY));
    }

    public static void launchBalanced() {
        if (launched.get()) {
            return;
        }

        launched.set(true);

        BigInteger day = _getDay();
        launchDay.set(day);
        BigInteger timeDelta =
                BigInteger.valueOf(Context.getBlockTimestamp()).add(timeOffset.getOrDefault(BigInteger.ZERO));

        launchTime.set(timeDelta);
        _setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
            call(ContractManager.getAddress(Names.REWARDS), "addNewDataSource", source.get("name"),
                    ContractManager.get(source.get("address")));
        }

        call(ContractManager.getAddress(Names.REWARDS), "updateBalTokenDistPercentage", (Object) RECIPIENTS);
    }

    public static void createBnusdMarket() {
        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = ContractManager.getAddress(Names.DEX);
        Address sICXAddress = ContractManager.getAddress(Names.SICX);
        Address bnUSDAddress = ContractManager.getAddress(Names.BNUSD);
        Address stakingAddress = ContractManager.getAddress(Names.STAKING);
        Address loansAddress = ContractManager.getAddress(Names.LOANS);

        BigInteger price = call(BigInteger.class, bnUSDAddress, "priceInLoop");
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        call(value.divide(BigInteger.valueOf(7)), stakingAddress, "stakeICX", Context.getAddress(),
                new byte[0]);
        call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount,
                Context.getAddress(), BigInteger.ZERO);

        BigInteger bnUSDValue = call(BigInteger.class, bnUSDAddress, "balanceOf", Context.getAddress());
        BigInteger sICXValue = call(BigInteger.class, sICXAddress, "balanceOf", Context.getAddress());

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(bnUSDAddress, "transfer", dexAddress, bnUSDValue, depositData.toString().getBytes());
        call(sICXAddress, "transfer", dexAddress, sICXValue, depositData.toString().getBytes());

        call(dexAddress, "add", sICXAddress, bnUSDAddress, sICXValue, bnUSDValue, false);
        String name = "sICX/bnUSD";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", sICXAddress, bnUSDAddress);
        call(dexAddress, "setMarketName", pid, name);

        _addLPDataSource(name, pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(225).multiply(pow(BigInteger.TEN, 15))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        call(ContractManager.getAddress(Names.REWARDS), "updateBalTokenDistPercentage", (Object) recipients);
    }

    public static void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        Address dexAddress = ContractManager.getAddress(Names.DEX);
        Address balnAddress = ContractManager.getAddress(Names.BALN);
        Address bnUSDAddress = ContractManager.getAddress(Names.BNUSD);
        Address rewardsAddress = ContractManager.getAddress(Names.REWARDS);
        Address loansAddress = ContractManager.getAddress(Names.LOANS);

        Object sources = new String[]{"Loans", "sICX/bnUSD"};
        call(rewardsAddress, "claimRewards", sources);
        call(loansAddress, "depositAndBorrow", "bnUSD", _bnUSD_amount, Context.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(bnUSDAddress, "transfer", dexAddress, _bnUSD_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount, false);
        String name = "BALN/bnUSD";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, bnUSDAddress);
        call(dexAddress, "setMarketName", pid, name);

        _addLPDataSource(name, pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        call(rewardsAddress, "updateBalTokenDistPercentage", (Object) recipients);
    }

    public static void createBalnSicxMarket(BigInteger _sicx_amount, BigInteger _baln_amount) {
        Address dexAddress = ContractManager.getAddress(Names.DEX);
        Address balnAddress = ContractManager.getAddress(Names.BALN);
        Address sICXAddress = ContractManager.getAddress(Names.SICX);
        Address rewardsAddress = ContractManager.getAddress(Names.REWARDS);

        Object sources = new String[]{"Loans", "sICX/bnUSD", "BALN/bnUSD"};
        call(rewardsAddress, "claimRewards", sources);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(sICXAddress, "transfer", dexAddress, _sicx_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, sICXAddress, _baln_amount, _sicx_amount, false);
        String name = "BALN/sICX";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, sICXAddress);
        call(dexAddress, "setMarketName", pid, name);

        _addLPDataSource(name, pid);

        DistributionPercentage[] recipients = new DistributionPercentage[]{
                createDistributionPercentage("Loans", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("DAOfund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/bnUSD", BigInteger.valueOf(15).multiply(pow(BigInteger.TEN, 16))),
                createDistributionPercentage("BALN/sICX", BigInteger.valueOf(10).multiply(pow(BigInteger.TEN, 16)))
        };

        call(ContractManager.getAddress(Names.REWARDS), "updateBalTokenDistPercentage", (Object) recipients);
    }

    private static void _addNewDataSource(String _data_source_name, String _contract_address) {
        Context.call(ContractManager.getAddress(Names.REWARDS), "addNewDataSource", _data_source_name,
                Address.fromString(_contract_address));
    }

    private static void _addLPDataSource(String _name, BigInteger _poolId) {
        Address stakedLP = ContractManager.getAddress(Names.STAKEDLP);
        Context.call(stakedLP, "addDataSource", _poolId, _name);
        _addNewDataSource(_name, stakedLP.toString());
    }
}
