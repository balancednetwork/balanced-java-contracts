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

package network.balanced.score.core.systemtest;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.Address;

class DexMirror implements ScoreIntegrationTest {
    private Balanced balanced;
    private Balanced referenceBalanced;
    private BalancedClient owner;
    private BalancedClient referenceOwner;
    private String dexJavaPath;
    private String loansJavaPath;
    private String rewardsJavaPath;
    private String dividendsJavaPath;
    private BigInteger EXA = BigInteger.TEN.pow(18);

    @BeforeEach    
    void setup() throws Exception {
        dexJavaPath = System.getProperty("Dex");
        loansJavaPath = System.getProperty("Loans");
        rewardsJavaPath = System.getProperty("Rewards");
        dividendsJavaPath = System.getProperty("Dividends");

        System.setProperty("Rewards", System.getProperty("rewardsPython"));
        System.setProperty("Dex", System.getProperty("dexPython"));
        System.setProperty("Loans", System.getProperty("loansPython"));
        System.setProperty("Dividends", System.getProperty("dividendsPython"));
        
        balanced = new Balanced();
        referenceBalanced = new Balanced();
        balanced.deployBalanced();
        referenceBalanced.deployBalanced();

        System.setProperty("Rewards", rewardsJavaPath);
        System.setProperty("Dex", dexJavaPath);
        System.setProperty("Loans", loansJavaPath);
        System.setProperty("Dividends", dividendsJavaPath);

        owner = balanced.ownerClient;
        referenceOwner = referenceBalanced.ownerClient;

        balanced.increaseDay(1);
        referenceBalanced.increaseDay(1);
        owner.baln.toggleEnableSnapshot();
        referenceOwner.baln.toggleEnableSnapshot();

        owner.governance.addAcceptedTokens(balanced.sicx._address().toString());
        owner.governance.addAcceptedTokens(balanced.baln._address().toString());
        owner.governance.addAcceptedTokens(balanced.bnusd._address().toString());
        owner.governance.setAcceptedDividendTokens(new score.Address[] {
                balanced.sicx._address(),
                balanced.baln._address(),
                balanced.bnusd._address()
            });

        referenceOwner.governance.addAcceptedTokens(referenceBalanced.sicx._address().toString());
        referenceOwner.governance.addAcceptedTokens(referenceBalanced.baln._address().toString());
        referenceOwner.governance.addAcceptedTokens(referenceBalanced.bnusd._address().toString());
        referenceOwner.governance.setAcceptedDividendTokens(new score.Address[] {
                referenceBalanced.sicx._address(),
                referenceBalanced.baln._address(),
                referenceBalanced.bnusd._address()
            });
    
        nextDay();
        balanced.dividends._update(dividendsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setDividendsOnlyToStakedBalnDay(owner.dividends.getSnapshotId().add(BigInteger.ONE));

        referenceBalanced.dividends._update(dividendsJavaPath, Map.of("_governance", referenceBalanced.governance._address()));
        referenceOwner.governance.setDividendsOnlyToStakedBalnDay(referenceOwner.dividends.getSnapshotId().add(BigInteger.ONE));

        
        updateRewards();
        balanced.loans._update(loansJavaPath, Map.of("_governance", balanced.governance._address()));
        referenceBalanced.loans._update(loansJavaPath, Map.of("_governance", referenceBalanced.governance._address()));
    }

    @Test
    void dexMirror() throws Exception {
        setupPositionsPython();
        setupPositionsPython();
        compareAllPositions();
        balanced.dex._update(dexJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.governance.setContractAddresses();

        compareAllPositions();
        setupPositions();
        setupPositions();
        compareAllPositions();
        nextDay();
        setupPositions();
        setupPositions();
        compareAllPositions();

    }

    private void setupPositionsPython() throws Exception {
        byte[] swapIcx = "{\"method\":\"_swap_icx\",\"params\":{\"none\":\"none\"}}".getBytes();

        BalancedClient emptyClient = balanced.newClient();
        BalancedClient sicxBnusdLP = balanced.newClient();
        BalancedClient icxSicxLp = balanced.newClient();
        BalancedClient balnLP1 = balanced.newClient();
        BalancedClient balnLP2 = balanced.newClient();
        BalancedClient sICXUnstaker = balanced.newClient();
        BalancedClient swapper = balanced.newClient();

        sicxBnusdLP.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP2.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        joinsICXBnusdLP(sicxBnusdLP, balanced, BigInteger.TEN.pow(22), BigInteger.TEN.pow(22));

        sicxBnusdLP.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);
        icxSicxLp.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);

        sICXUnstaker.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        sICXUnstaker.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapIcx);

        String swapToBaln = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.baln._address()+"\"}}";
        String swapToBnusd = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.bnusd._address()+"\"}}";
        String swapToSicx = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.sicx._address()+"\"}}";
        swapper.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        swapper.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);

        swapper.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToBaln.getBytes());
        swapper.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapToBnusd.getBytes());
        swapper.bnUSD.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapToBaln.getBytes());
        swapper.bnUSD.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToSicx.getBytes());
        swapper.baln.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToBnusd.getBytes());
        swapper.baln.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToSicx.getBytes());
    

        BalancedClient refemptyClient = referenceBalanced.newClient();
        BalancedClient refsicxBnusdLP = referenceBalanced.newClient();
        BalancedClient reficxSicxLp = referenceBalanced.newClient();
        BalancedClient refbalnLP1 = referenceBalanced.newClient();
        BalancedClient refbalnLP2 = referenceBalanced.newClient();
        BalancedClient refsICXUnstaker = referenceBalanced.newClient();
        BalancedClient refswapper = referenceBalanced.newClient();

        refsicxBnusdLP.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP2.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        joinsICXBnusdLP(refsicxBnusdLP, referenceBalanced, BigInteger.TEN.pow(22), BigInteger.TEN.pow(22));

        refsicxBnusdLP.dex._transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), null);
        reficxSicxLp.dex._transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), null);

        refsICXUnstaker.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        refsICXUnstaker.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), swapIcx);

        String refswapToBaln = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.baln._address()+"\"}}";
        String refswapToBnusd = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.bnusd._address()+"\"}}";
        String refswapToSicx = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.sicx._address()+"\"}}";
        refswapper.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        refswapper.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);

        refswapper.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToBaln.getBytes());
        refswapper.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), refswapToBnusd.getBytes());
        refswapper.bnUSD.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), refswapToBaln.getBytes());
        refswapper.bnUSD.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToSicx.getBytes());
        refswapper.baln.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToBnusd.getBytes());
        refswapper.baln.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToSicx.getBytes());

        nextDay();
        BigInteger balnLP1balance = verifyRewards(balnLP1);
        BigInteger balnLP2balance = verifyRewards(balnLP2);
        BigInteger refbalnLP1balance = verifyRewards(refbalnLP1);
        BigInteger refbalnLP2balance = verifyRewards(refbalnLP2);
        BigInteger LP1Amount = balnLP1balance.min(refbalnLP1balance);
        BigInteger LP2Amount = balnLP2balance.min(refbalnLP2balance);
        
        joinsICXBalnLP(balnLP1, balanced, LP1Amount, LP1Amount);
        joinBalnBnusdLP(balnLP2, balanced, LP2Amount, BigInteger.TEN.pow(22));
        sICXUnstaker.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapIcx);

        joinsICXBalnLP(refbalnLP1, referenceBalanced, LP1Amount, LP1Amount);
        joinBalnBnusdLP(refbalnLP2, referenceBalanced, LP2Amount, BigInteger.TEN.pow(22));
        refsICXUnstaker.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), swapIcx);
    }

    private void setupPositions() throws Exception {
        byte[] swapIcx = "{\"method\":\"_swap_icx\",\"params\":{\"none\":\"none\"}}".getBytes();

        BalancedClient emptyClient = balanced.newClient();
        BalancedClient sicxBnusdLP = balanced.newClient();
        BalancedClient icxSicxLp = balanced.newClient();
        BalancedClient balnLP1 = balanced.newClient();
        BalancedClient balnLP2 = balanced.newClient();
        BalancedClient sICXUnstaker = balanced.newClient();
        BalancedClient swapper = balanced.newClient();

        sicxBnusdLP.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        balnLP2.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        joinsICXBnusdLP(sicxBnusdLP, balanced, BigInteger.TEN.pow(22), BigInteger.TEN.pow(22));
        stakeICXBnusdLP(sicxBnusdLP);
        unstakeICXBnusdLP(sicxBnusdLP);
        stakeICXBnusdLP(sicxBnusdLP);

        sicxBnusdLP.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);
        icxSicxLp.dex._transfer(balanced.dex._address(), BigInteger.TEN.pow(22), null);

        sICXUnstaker.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        sICXUnstaker.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapIcx);

        String swapToBaln = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.baln._address()+"\"}}";
        String swapToBnusd = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.bnusd._address()+"\"}}";
        String swapToSicx = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+balanced.sicx._address()+"\"}}";
        swapper.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        swapper.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);

        swapper.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToBaln.getBytes());
        swapper.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapToBnusd.getBytes());
        swapper.bnUSD.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapToBaln.getBytes());
        swapper.bnUSD.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToSicx.getBytes());
        swapper.baln.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToBnusd.getBytes());
        swapper.baln.transfer(balanced.dex._address(), BigInteger.TEN.pow(21), swapToSicx.getBytes());
    

        BalancedClient refemptyClient = referenceBalanced.newClient();
        BalancedClient refsicxBnusdLP = referenceBalanced.newClient();
        BalancedClient reficxSicxLp = referenceBalanced.newClient();
        BalancedClient refbalnLP1 = referenceBalanced.newClient();
        BalancedClient refbalnLP2 = referenceBalanced.newClient();
        BalancedClient refsICXUnstaker = referenceBalanced.newClient();
        BalancedClient refswapper = referenceBalanced.newClient();

        refsicxBnusdLP.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP1.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP2.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);
        refbalnLP2.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        joinsICXBnusdLP(refsicxBnusdLP, referenceBalanced, BigInteger.TEN.pow(22), BigInteger.TEN.pow(22));

        refsicxBnusdLP.dex._transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), null);
        reficxSicxLp.dex._transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), null);

        refsICXUnstaker.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        refsICXUnstaker.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), swapIcx);

        String refswapToBaln = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.baln._address()+"\"}}";
        String refswapToBnusd = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.bnusd._address()+"\"}}";
        String refswapToSicx = "{\"method\":\"_swap\",\"params\":{\"toToken\":\""+referenceBalanced.sicx._address()+"\"}}";
        refswapper.staking.stakeICX(BigInteger.TEN.pow(23), null, null);
        refswapper.loans.depositAndBorrow(BigInteger.TEN.pow(23), "bnUSD", BigInteger.TEN.pow(22), null, null);

        refswapper.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToBaln.getBytes());
        refswapper.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), refswapToBnusd.getBytes());
        refswapper.bnUSD.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), refswapToBaln.getBytes());
        refswapper.bnUSD.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToSicx.getBytes());
        refswapper.baln.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToBnusd.getBytes());
        refswapper.baln.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(21), refswapToSicx.getBytes());

        nextDay();
        BigInteger balnLP1balance = verifyRewards(balnLP1);
        BigInteger balnLP2balance = verifyRewards(balnLP2);
        BigInteger refbalnLP1balance = verifyRewards(refbalnLP1);
        BigInteger refbalnLP2balance = verifyRewards(refbalnLP2);
        BigInteger LP1Amount = balnLP1balance.min(refbalnLP1balance);
        BigInteger LP2Amount = balnLP2balance.min(refbalnLP2balance);
        
        joinsICXBalnLP(balnLP1, balanced, LP1Amount, LP1Amount);
        joinBalnBnusdLP(balnLP2, balanced, LP2Amount, BigInteger.TEN.pow(22));
        sICXUnstaker.sicx.transfer(balanced.dex._address(), BigInteger.TEN.pow(22), swapIcx);
        stakeICXBalnLP(balnLP1);
        unstakeICXBnusdLP(sicxBnusdLP);

        joinsICXBalnLP(refbalnLP1, referenceBalanced, LP1Amount, LP1Amount);
        joinBalnBnusdLP(refbalnLP2, referenceBalanced, LP2Amount, BigInteger.TEN.pow(22));
        refsICXUnstaker.sicx.transfer(referenceBalanced.dex._address(), BigInteger.TEN.pow(22), swapIcx);
    }

    private void compareAllPositions() throws Exception {
        for (int i = 0; i < balanced.balancedClientsList.size(); i++) {
            BalancedClient client = balanced.getClient(balanced.balancedClientsList.get(i));
            BalancedClient referenceclient = referenceBalanced.getClient(referenceBalanced.balancedClientsList.get(i));
            comparePositions(client, referenceclient);

            compareValues("sicxBalance", 
                referenceclient.sicx.balanceOf(referenceclient.getAddress()), 
                client.sicx.balanceOf(client.getAddress()));
            compareValues("bnusdBalance",          
                referenceclient.bnUSD.balanceOf(referenceclient.getAddress()), 
                client.bnUSD.balanceOf(client.getAddress()));;
            compareValues("balnBalance",          
                referenceclient.baln.balanceOf(referenceclient.getAddress()), 
                client.baln.balanceOf(client.getAddress()));
        }

        assertEquals(referenceOwner.staking.getTodayRate(), owner.staking.getTodayRate());
        compareDex();
    }


    private void comparePositions(BalancedClient client, BalancedClient referenceclient) throws Exception {
        BigInteger day = owner.governance.getDay();
        List<String> namedPools = owner.dex.getNamedPools();
        BigInteger sicxEarnings = owner.dex.getSicxEarnings(client.getAddress());
        List<Boolean> withdrawLocks = new ArrayList<>();
        for (String name : namedPools) {
            BigInteger pid = owner.dex.lookupPid(name);
            withdrawLocks.add(owner.dex.getWithdrawLock(pid, client.getAddress()).equals(BigInteger.ZERO));
        }

        BigInteger ICXBalance = owner.dex.getICXBalance(client.getAddress());
        List<BigInteger> balances = new ArrayList<>();
        for (String name : namedPools) {
            BigInteger pid = owner.dex.lookupPid(name);
            balances.add(owner.dex.balanceOf(client.getAddress(), pid));
        }

        //#########################//#endregion

        List<String> refnamedPools = referenceOwner.dex.getNamedPools();
        BigInteger refsicxEarnings = referenceOwner.dex.getSicxEarnings(referenceclient.getAddress());
        List<Boolean> refwithdrawLocks = new ArrayList<>();
        for (String name : refnamedPools) {
            BigInteger pid = referenceOwner.dex.lookupPid(name);
            refwithdrawLocks.add(referenceOwner.dex.getWithdrawLock(pid, referenceclient.getAddress()).equals(BigInteger.ZERO));
        }

        BigInteger refICXBalance = referenceOwner.dex.getICXBalance(referenceclient.getAddress());
        List<BigInteger> refbalances = new ArrayList<>();
        for (String name : refnamedPools) {
            BigInteger pid = referenceOwner.dex.lookupPid(name);
            refbalances.add(referenceOwner.dex.balanceOf(referenceclient.getAddress(), pid));
        }

        
        assertEquals(refnamedPools.toString(), namedPools.toString());
        assertEquals(refsicxEarnings, sicxEarnings);
        assertEquals(refwithdrawLocks.toString(), withdrawLocks.toString());
        assertEquals(refICXBalance, ICXBalance);
        for (int i = 0; i < refbalances.size(); i++) {
            compareValues(refnamedPools.get(i), refbalances.get(i), balances.get(i));
            
        }
    }


    private void compareDex() throws Exception {
        BigInteger day = owner.governance.getDay();

        List<String> namedPools = owner.dex.getNamedPools();
        BigInteger nonce = owner.dex.getNonce();

        List<BigInteger> poolTotals = new ArrayList<>();
        List<BigInteger> totalSupplies = new ArrayList<>();
        List<BigInteger> prices = new ArrayList<>();
        List<Map<String, Object>> stats = new ArrayList<>();
        List<BigInteger> totalDexAddresses = new ArrayList<>();

        List<BigInteger> totalSuppliesAt = new ArrayList<>();
        List<BigInteger> totalBalnAt = new ArrayList<>();
        List<BigInteger> totalValues = new ArrayList<>();
        List<BigInteger> totalBalnSnapshots = new ArrayList<>();
        List<Map<String,Object>> dataBatches = new ArrayList<>();

        for (String name : namedPools) {
            BigInteger pid = owner.dex.lookupPid(name);
            Address base = owner.dex.getPoolBase(pid);
            Address quote = owner.dex.getPoolQuote(pid);
            try {
                poolTotals.add(owner.dex.getPoolTotal(pid, base));
                poolTotals.add(owner.dex.getPoolTotal(pid, quote));
            } catch (Exception e) {}
           
            totalSupplies.add(owner.dex.totalSupply(pid));

            prices.add(owner.dex.getBasePriceInQuote(pid));
            prices.add(owner.dex.getPrice(pid));
            prices.add(owner.dex.getBnusdValue(name));
            prices.add(owner.dex.getPriceByName(name));

            stats.add(owner.dex.getPoolStats(pid));
            
            totalDexAddresses.add(owner.dex.totalDexAddresses(pid));

            totalSuppliesAt.add(owner.dex.totalSupplyAt(pid, day, false));
            totalSuppliesAt.add(owner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(1)), false));
            totalSuppliesAt.add(owner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(2)), false));
            totalSuppliesAt.add(owner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(3)), false));

            totalBalnAt.add(owner.dex.totalBalnAt(pid, day, false));
            totalBalnAt.add(owner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(1)), false));
            totalBalnAt.add(owner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(2)), false));
            totalBalnAt.add(owner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(3)), false));

            totalValues.add(owner.dex.getTotalValue(name, day));
            totalValues.add(owner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(1))));
            totalValues.add(owner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(2))));
            totalValues.add(owner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(3))));

            totalBalnSnapshots.add(owner.dex.getBalnSnapshot(name, day));
            totalBalnSnapshots.add(owner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(1))));
            totalBalnSnapshots.add(owner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(2))));
            totalBalnSnapshots.add(owner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(3))));

            BigInteger limit = owner.dex.totalDexAddresses(pid);
            dataBatches.add(owner.dex.getDataBatch(name, BigInteger.valueOf(1), limit, BigInteger.ZERO));
            dataBatches.add(owner.dex.getDataBatch(name, BigInteger.valueOf(2), limit, BigInteger.ZERO));
            dataBatches.add(owner.dex.getDataBatch(name, BigInteger.valueOf(3), limit, BigInteger.ZERO));

            dataBatches.add(owner.dex.getDataBatch(name, day, limit, BigInteger.ZERO));
            dataBatches.add(owner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(1)), limit, BigInteger.ZERO));
            dataBatches.add(owner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(2)), limit, BigInteger.ZERO));
            dataBatches.add(owner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(3)), limit, BigInteger.ZERO));
        }

        Map<String, BigInteger> fees = owner.dex.getFees();
        BigInteger balnPrice = owner.dex.getBalnPrice();
        BigInteger sicxBnusdPrice = owner.dex.getSicxBnusdPrice();

        //####################

        List<String> refnamedPools = owner.dex.getNamedPools();
        BigInteger refnonce = owner.dex.getNonce();

        List<BigInteger> refpoolTotals = new ArrayList<>();
        List<BigInteger> reftotalSupplies = new ArrayList<>();
        List<BigInteger> refprices = new ArrayList<>();
        List<Map<String, Object>> refstats = new ArrayList<>();
        List<BigInteger> reftotalDexAddresses = new ArrayList<>();

        List<BigInteger> reftotalSuppliesAt = new ArrayList<>();
        List<BigInteger> reftotalBalnAt = new ArrayList<>();
        List<BigInteger> reftotalValues = new ArrayList<>();
        List<BigInteger> reftotalBalnSnapshots = new ArrayList<>();
        List<Map<String,Object>> refdataBatches = new ArrayList<>();

        for (String name : refnamedPools) {
            BigInteger pid = referenceOwner.dex.lookupPid(name);
            Address base = referenceOwner.dex.getPoolBase(pid);
            Address quote = referenceOwner.dex.getPoolQuote(pid);
            try {
                refpoolTotals.add(referenceOwner.dex.getPoolTotal(pid, base));
                refpoolTotals.add(referenceOwner.dex.getPoolTotal(pid, quote));
            } catch (Exception e) {}
           
            reftotalSupplies.add(referenceOwner.dex.totalSupply(pid));

            refprices.add(referenceOwner.dex.getBasePriceInQuote(pid));
            refprices.add(referenceOwner.dex.getPrice(pid));
            refprices.add(referenceOwner.dex.getBnusdValue(name));
            refprices.add(referenceOwner.dex.getPriceByName(name));

            refstats.add(referenceOwner.dex.getPoolStats(pid));
            
            reftotalDexAddresses.add(referenceOwner.dex.totalDexAddresses(pid));

            reftotalSuppliesAt.add(referenceOwner.dex.totalSupplyAt(pid, day, false));
            reftotalSuppliesAt.add(referenceOwner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(1)), false));
            reftotalSuppliesAt.add(referenceOwner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(2)), false));
            reftotalSuppliesAt.add(referenceOwner.dex.totalSupplyAt(pid, day.subtract(BigInteger.valueOf(3)), false));

            reftotalBalnAt.add(referenceOwner.dex.totalBalnAt(pid, day, false));
            reftotalBalnAt.add(referenceOwner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(1)), false));
            reftotalBalnAt.add(referenceOwner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(2)), false));
            reftotalBalnAt.add(referenceOwner.dex.totalBalnAt(pid, day.subtract(BigInteger.valueOf(3)), false));

            reftotalValues.add(referenceOwner.dex.getTotalValue(name, day));
            reftotalValues.add(referenceOwner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(1))));
            reftotalValues.add(referenceOwner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(2))));
            reftotalValues.add(referenceOwner.dex.getTotalValue(name, day.subtract(BigInteger.valueOf(3))));

            reftotalBalnSnapshots.add(referenceOwner.dex.getBalnSnapshot(name, day));
            reftotalBalnSnapshots.add(referenceOwner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(1))));
            reftotalBalnSnapshots.add(referenceOwner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(2))));
            reftotalBalnSnapshots.add(referenceOwner.dex.getBalnSnapshot(name, day.subtract(BigInteger.valueOf(3))));

            BigInteger limit = referenceOwner.dex.totalDexAddresses(pid);
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, BigInteger.valueOf(1), limit, BigInteger.ZERO));
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, BigInteger.valueOf(2), limit, BigInteger.ZERO));
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, BigInteger.valueOf(3), limit, BigInteger.ZERO));

            refdataBatches.add(referenceOwner.dex.getDataBatch(name, day, limit, BigInteger.ZERO));
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(1)), limit, BigInteger.ZERO));
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(2)), limit, BigInteger.ZERO));
            refdataBatches.add(referenceOwner.dex.getDataBatch(name, day.subtract(BigInteger.valueOf(3)), limit, BigInteger.ZERO));
        }

        Map<String, BigInteger> reffees = referenceOwner.dex.getFees();
        BigInteger refbalnPrice = referenceOwner.dex.getBalnPrice();
        BigInteger refsicxBnusdPrice = referenceOwner.dex.getSicxBnusdPrice();

        assertEquals(refnamedPools.toString(), namedPools.toString());
        assertEquals(refnonce.toString(), nonce.toString());
        compareValues("PoolTotal", refpoolTotals, poolTotals);
        compareValues("TotalSupply",reftotalSupplies, totalSupplies);
        compareValues("Prices", refprices, prices);
        assertEquals(refstats.toString().replaceAll("cx[0-9A-Ha-h]{40}", ""), stats.toString().replaceAll("cx[0-9A-Ha-h]{40}", ""));
        assertEquals(reftotalDexAddresses.toString(), totalDexAddresses.toString());
        compareValues("TotalSupplyAt", reftotalSuppliesAt, totalSuppliesAt);
        compareValues("totalBalnAt", reftotalBalnAt, totalBalnAt);
        compareValues("TotalValues", reftotalValues, totalValues);
        compareValues("totalBalnSnapshots", reftotalBalnSnapshots, totalBalnSnapshots);
        assertEquals(reffees.toString(), fees.toString());
        compareValues("balnPrice" , refbalnPrice, balnPrice);
        compareValues("sICX/Bnusd price", refsicxBnusdPrice, sicxBnusdPrice);
        String refdataBatchesString = refdataBatches.toString().replaceAll("cx[0-9A-Ha-h]{40}", "").replaceAll("hx[0-9A-Ha-h]{40}", "");
        String dataBatchesString = dataBatches.toString().replaceAll("cx[0-9A-Ha-h]{40}", "").replaceAll("hx[0-9A-Ha-h]{40}", "");
        char[] refdataBatchesArray = refdataBatchesString.toCharArray();
        char[] dataBatchesArray = dataBatchesString.toCharArray();
        Arrays.sort(refdataBatchesArray);
        Arrays.sort(dataBatchesArray);
        assertEquals(new String(refdataBatchesArray), new String(dataBatchesArray));
    }
    
    private void compareValues(String name, List<BigInteger> reference, List<BigInteger> base) {
        for (int i = 0; i < reference.size(); i++) {
            compareValues(name, reference.get(i), base.get(i));
        }
    }

    private void compareValues(String name, BigInteger reference, BigInteger base) {
        BigInteger diff = reference.subtract(base);
        if (!diff.equals(BigInteger.ZERO)) {
            System.out.println(name +": " + diff);
        }
    }
    
    private void updateRewards() {
        balanced.rewards._update(rewardsJavaPath, Map.of("_governance", balanced.governance._address()));
        owner.rewards.addDataProvider(balanced.stakedLp._address());
        owner.rewards.addDataProvider(balanced.dex._address());
        owner.rewards.addDataProvider(balanced.loans._address());

        referenceBalanced.rewards._update(rewardsJavaPath, Map.of("_governance", referenceBalanced.governance._address()));
        referenceOwner.rewards.addDataProvider(referenceBalanced.stakedLp._address());
        referenceOwner.rewards.addDataProvider(referenceBalanced.dex._address());
        referenceOwner.rewards.addDataProvider(referenceBalanced.loans._address());
    }

    private void joinsICXBnusdLP(BalancedClient client, Balanced targetBalanced, BigInteger icxAmount, BigInteger bnusdAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.bnUSD.transfer(targetBalanced.dex._address(), bnusdAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(targetBalanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(targetBalanced.sicx._address(), targetBalanced.bnusd._address(), sicxDeposit, bnusdAmount, true);
    }

    private void leaveICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = client.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.remove(icxBnusdPoolId, poolBalance, true);
    }

    private void joinsICXBalnLP(BalancedClient client,  Balanced targetBalanced, BigInteger icxAmount, BigInteger balnAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.baln.transfer(targetBalanced.dex._address(), balnAmount, depositData.toString().getBytes());
        client.staking.stakeICX(icxAmount, null, null);

        BigInteger sicxDeposit = client.sicx.balanceOf(client.getAddress());
        client.sicx.transfer(targetBalanced.dex._address(), sicxDeposit, depositData.toString().getBytes());
        client.dex.add(targetBalanced.baln._address(), targetBalanced.sicx._address(), balnAmount, sicxDeposit, true);
    }

    private void joinBalnBnusdLP(BalancedClient client, Balanced targetBalanced, BigInteger balnAmount, BigInteger bnusdAmount) {
        JsonObject depositData = Json.object();
        depositData.add("method", "_deposit");
        client.baln.transfer(targetBalanced.dex._address(), balnAmount, depositData.toString().getBytes());

        client.bnUSD.transfer(targetBalanced.dex._address(), bnusdAmount, depositData.toString().getBytes());
        client.dex.add(targetBalanced.baln._address(), targetBalanced.bnusd._address(), balnAmount, bnusdAmount, true);
    }

    private void stakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBnusdPoolId, null);
    }

    private void unstakeICXBnusdLP(BalancedClient client) {
        BigInteger icxBnusdPoolId = owner.dex.getPoolId(balanced.sicx._address(), balanced.bnusd._address());
        BigInteger poolBalance = client.stakedLp.balanceOf(client.getAddress(), icxBnusdPoolId);
        client.stakedLp.unstake(icxBnusdPoolId, poolBalance);
    }

    private void stakeICXBalnLP(BalancedClient client) {
        BigInteger icxBalnPoolId = owner.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        BigInteger poolBalance = client.dex.balanceOf(client.getAddress(), icxBalnPoolId);
        client.dex.transfer(balanced.stakedLp._address(), poolBalance, icxBalnPoolId, null);
    }

    private void stakeBaln(BalancedClient client) {
        BigInteger balance = client.baln.balanceOf(client.getAddress());
        client.baln.stake(balance);
    }

    private BigInteger verifyRewards(BalancedClient client) {
        BigInteger balancePreClaim = client.baln.balanceOf(client.getAddress());
        client.rewards.claimRewards();
        BigInteger balancePostClaim = client.baln.balanceOf(client.getAddress());
        assertTrue(balancePostClaim.compareTo(balancePreClaim) > 0);

        return balancePostClaim.subtract(balancePreClaim);
    }
    
    private void nextDay() {
        balanced.increaseDay(1);
        balanced.syncDistributions();

        referenceBalanced.increaseDay(1);
        referenceBalanced.syncDistributions();
    }
}