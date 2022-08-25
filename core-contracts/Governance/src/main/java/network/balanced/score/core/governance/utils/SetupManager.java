package network.balanced.score.core.governance.utils;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import network.balanced.score.lib.structs.DistributionPercentage;

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
                    ContractManager.get("loans"),
                    "addAsset",
                    tokenAddress,
                    asset.get("active"),
                    asset.get("collateral")
            );
            call(ContractManager.get("dividends"), "addAcceptedTokens", tokenAddress);
        }

        Address[] acceptedFeeTokens = new Address[]{
            ContractManager.get("sicx"), 
            ContractManager.get("bnUSD"), 
            ContractManager.get("baln")
        };

        call(ContractManager.get("feehandler"), "setAcceptedDividendTokens", (Object) acceptedFeeTokens);
    }

    public static void launchBalanced() {
        if (launched.get()) {
            return;
        }

        launched.set(true);

        BigInteger day = _getDay();
        launchDay.set(day);
        BigInteger timeDelta = BigInteger.valueOf(Context.getBlockTimestamp()).add(timeOffset.getOrDefault(BigInteger.ZERO));

        launchTime.set(timeDelta);
        _setTimeOffset(timeDelta);

        for (Map<String, String> source : DATA_SOURCES) {
            call(ContractManager.get("rewards"), "addNewDataSource", source.get("name"), ContractManager.get(source.get("address")));
        }

        call(ContractManager.get("rewards"), "updateBalTokenDistPercentage", (Object) RECIPIENTS);
    }

    public static void createBnusdMarket() {
        BigInteger value = Context.getValue();
        Context.require(!value.equals(BigInteger.ZERO), TAG + "ICX sent must be greater than zero.");

        Address dexAddress = ContractManager.get("dex");
        Address sICXAddress = ContractManager.get("sicx");
        Address bnUSDAddress = ContractManager.get("bnUSD");
        Address stakedLpAddress = ContractManager.get("stakedLp");
        Address stakingAddress = ContractManager.get("staking");
        Address rewardsAddress = ContractManager.get("rewards");
        Address loansAddress = ContractManager.get("loans");

        BigInteger price = call(BigInteger.class, bnUSDAddress, "priceInLoop");
        BigInteger amount = EXA.multiply(value).divide(price.multiply(BigInteger.valueOf(7)));
        call(value.divide(BigInteger.valueOf(7)), stakingAddress, "stakeICX", Context.getAddress(),
                new byte[0]);
        call(Context.getBalance(Context.getAddress()), loansAddress, "depositAndBorrow", "bnUSD", amount, Context.getAddress(), BigInteger.ZERO);

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

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);
        DistributionPercentage[] recipients = new DistributionPercentage[]{
            createDistributionPercentage("Loans", BigInteger.valueOf(25).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("sICX/ICX", BigInteger.TEN.multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Worker Tokens", BigInteger.valueOf(20).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("Reserve Fund", BigInteger.valueOf(5).multiply(pow(BigInteger.TEN, 16))),
            createDistributionPercentage("DAOfund", BigInteger.valueOf(225).multiply(pow(BigInteger.TEN, 15))),
            createDistributionPercentage("sICX/bnUSD", BigInteger.valueOf(175).multiply(pow(BigInteger.TEN, 15)))
        };

        call(ContractManager.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }

    public static void createBalnMarket(BigInteger _bnUSD_amount, BigInteger _baln_amount) {
        Address dexAddress = ContractManager.get("dex");
        Address balnAddress = ContractManager.get("baln");
        Address bnUSDAddress = ContractManager.get("bnUSD");
        Address stakedLpAddress = ContractManager.get("stakedLp");
        Address rewardsAddress = ContractManager.get("rewards");
        Address loansAddress = ContractManager.get("loans");

        call(rewardsAddress, "claimRewards");
        call(loansAddress, "depositAndBorrow", "bnUSD", _bnUSD_amount, Context.getAddress(), BigInteger.ZERO);

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(bnUSDAddress, "transfer", dexAddress, _bnUSD_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, bnUSDAddress, _baln_amount, _bnUSD_amount, false);
        String name = "BALN/bnUSD";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, bnUSDAddress);
        call(dexAddress, "setMarketName", pid, name);

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);

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
        Address dexAddress = ContractManager.get("dex");
        Address balnAddress = ContractManager.get("baln");
        Address sICXAddress = ContractManager.get("sicx");
        Address stakedLpAddress = ContractManager.get("stakedLp");
        Address rewardsAddress = ContractManager.get("rewards");

        call(rewardsAddress, "claimRewards");

        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        call(sICXAddress, "transfer", dexAddress, _sicx_amount, depositData.toString().getBytes());
        call(balnAddress, "transfer", dexAddress, _baln_amount, depositData.toString().getBytes());

        call(dexAddress, "add", balnAddress, sICXAddress, _baln_amount, _sicx_amount, false);
        String name = "BALN/sICX";
        BigInteger pid = call(BigInteger.class, dexAddress, "getPoolId", balnAddress, sICXAddress);
        call(dexAddress, "setMarketName", pid, name);

        call(rewardsAddress, "addNewDataSource", name, dexAddress);
        call(stakedLpAddress, "addPool", pid);

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

        call(ContractManager.get("rewards"), "updateBalTokenDistPercentage", (Object) recipients);
    }
}
