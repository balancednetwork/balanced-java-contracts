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

package network.balanced.score.util.burner;

import network.balanced.score.lib.utils.Names;
import network.balanced.score.lib.utils.Versions;
import network.balanced.score.lib.interfaces.ICONBurner;
import score.*;
import score.annotation.External;
import score.annotation.Payable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import static network.balanced.score.lib.utils.Check.onlyOwner;
import static network.balanced.score.lib.utils.Constants.EXA;
import static network.balanced.score.lib.utils.Constants.POINTS;
import static network.balanced.score.lib.utils.Constants.SYSTEM_ADDRESS;

import static network.balanced.score.lib.utils.BalancedAddressManager.*;

public class ICONBurnerImpl implements ICONBurner {
    private static final VarDB<String> currentVersion = Context.newVarDB("VERSION", String.class);
    private static final VarDB<BigInteger> totalBurn = Context.newVarDB("TOTAL_BURN", BigInteger.class);

    // Points(0-10000)
    private static final VarDB<BigInteger> swapSlippage = Context.newVarDB("MAX_SWAP_SLIPPAGE", BigInteger.class);

    public static final BigInteger DEFAULT_SWAP_SLIPPAGE = BigInteger.valueOf(100); // 1%

    public ICONBurnerImpl(Address _governance) {
        if (currentVersion.get() == null) {
            setGovernance(_governance);
            swapSlippage.set(DEFAULT_SWAP_SLIPPAGE);
        }

        if (currentVersion.getOrDefault("").equals(Versions.BURNER)) {
            Context.revert("Can't Update same version of code");
        }

        currentVersion.set(Versions.BURNER);
    }

    @External(readonly = true)
    public String name() {
        return Names.BURNER;
    }

    @External(readonly = true)
    public String version() {
        return currentVersion.getOrDefault("");
    }

    @External(readonly = true)
    public BigInteger getSwapSlippage() {
        return swapSlippage.get();
    }

    @External
    public void setSwapSlippage(BigInteger _swapSlippage) {
        onlyOwner();
        swapSlippage.set(_swapSlippage);
    }

    @External(readonly = true)
    public BigInteger getBurnedAmount() {
        return totalBurn.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger getPendingBurn() {
        BigInteger sICXbalance = Context.call(BigInteger.class, getSicx(), "balanceOf", Context.getAddress());
        BigInteger bnUSDBalance = Context.call(BigInteger.class, getBnusd(), "balanceOf", Context.getAddress());
        BigInteger sICXPrice = Context.call(BigInteger.class, getBalancedOracle(), "getLastPriceInLoop", "sICX");
        BigInteger bnUSDPrice = Context.call(BigInteger.class, getBalancedOracle(), "getLastPriceInLoop", "bnUSD");
        return sICXPrice.multiply(sICXbalance).divide(EXA).add(bnUSDBalance.multiply(bnUSDPrice).divide(EXA));
    }

    @External(readonly = true)
    public BigInteger getUnstakingBurn() {
        List<Map<String, Object>> unstakeData =  (List<Map<String, Object>>) Context.call(getStaking(), "getUserUnstakeInfo", Context.getAddress());
        BigInteger total = BigInteger.ZERO;
        for (Map<String,Object> data : unstakeData) {
            total = total.add((BigInteger)data.get("amount"));
        }
        BigInteger claimableICX = Context.call(BigInteger.class, getStaking(), "claimableICX", Context.getAddress());

        return total.add(claimableICX);
    }

    @External
    public void swapBnUSD(BigInteger amount) {
        BigInteger sICXPriceInUSD = Context.call(BigInteger.class, getBalancedOracle(), "getPriceInUSD", "sICX");
        BigInteger sICXAmount = amount.multiply(EXA).divide(sICXPriceInUSD);
        BigInteger minReceive = (POINTS.subtract(swapSlippage.get())).multiply(sICXAmount).divide(POINTS);

         JsonObject swapParams = Json.object()
                .add("toToken", getSicx().toString())
                .add("minimumReceive", minReceive.toString());
        JsonObject swapData = Json.object()
                .add("method", "_swap")
                .add("params", swapParams);
        byte[] data = swapData.toString().getBytes();

        Context.call(getBnusd(), "transfer", getDex(), amount, data);
    }

    @External
    public void burn() {
        trySwap();
        unstake();
        claimUnstakedICX();
        _burn();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }

    @Payable
    public void fallback() {
    }

    private void trySwap() {
        BigInteger bnUSDBalance = Context.call(BigInteger.class, getBnusd(), "balanceOf", Context.getAddress());
        try {
            swapBnUSD(bnUSDBalance);
        } catch (Exception e) {

        }
    }

    private void unstake() {
        BigInteger balance = Context.call(BigInteger.class, getSicx(), "balanceOf", Context.getAddress());
        if (balance.equals(BigInteger.ZERO)) {
            return;
        }

        JsonObject unstakeData = Json.object()
                .add("method", "unstake");
        byte[] data = unstakeData.toString().getBytes();
        Context.call(getSicx(), "transfer", getStaking(), balance, data);
    }

    private void claimUnstakedICX() {
        try {
            Context.call(getStaking(), "claimUnstakedICX", Context.getAddress());
        } catch (Exception e) {
        }

    }

    private void _burn() {
        BigInteger balance = Context.getBalance(Context.getAddress());
        if (balance.equals(BigInteger.ZERO)) {
            return;
        }

        totalBurn.set(getBurnedAmount().add(balance));
        callBurn(balance);
    }

    public void callBurn(BigInteger amount) {
        Context.call(amount, SYSTEM_ADDRESS, "burn");
    }



}
