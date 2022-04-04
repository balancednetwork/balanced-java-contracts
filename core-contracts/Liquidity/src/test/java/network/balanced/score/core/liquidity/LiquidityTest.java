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

package network.balanced.score.core.liquidity;

import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.Account;
import com.iconloop.score.token.irc2.IRC2Basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import score.Address;
import score.Context;

import java.util.Map;

import java.math.BigInteger;


public class LiquidityTest extends TestBase {
    public static final ServiceManager sm = getServiceManager(); 
    public static final Account owner = sm.createAccount();

    private Score liquidity;
    private static Score sicx;
    private static Score bnusd;

    public static class IRC2BasicToken extends IRC2Basic {
        public IRC2BasicToken(String _name, String _symbol, int _decimals, BigInteger _totalSupply) {
            super(_name, _symbol, _decimals);
            _mint(Context.getCaller(), _totalSupply);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        liquidity = sm.deploy(owner, Liquidity.class);
        sicx = deployIRC2Basic(owner, "Staked icx", "sicx", 18, BigInteger.valueOf(1000));
        bnusd = deployIRC2Basic(owner, "Balanced usd", "bnusd", 18, BigInteger.valueOf(1000));
    }

    @Test
    void name() {
        String contractName = "Balanced Liquidity";
        assertEquals(contractName, liquidity.call("name"));
    }

    @Test
    void setGetDex() {
        assertNull(liquidity.call("getDex"));
        Account dex = Account.newScoreAccount(1);

        liquidity.invoke(owner, "setDex", dex.getAddress());

        assertEquals(dex.getAddress(), liquidity.call("getDex"));
    }
    
    @Test
    void setGetDaofund() {
        assertNull(liquidity.call("getDaofund"));
        Account daofund = Account.newScoreAccount(1);

        liquidity.invoke(owner, "setDaofund", daofund.getAddress());

        assertEquals(daofund.getAddress(), liquidity.call("getDaofund"));
    }

    @Test
    void setGetStakedLP() {
        assertNull(liquidity.call("getStakedLP"));
        Account stakedLP = Account.newScoreAccount(1);

        liquidity.invoke(owner, "setStakedLP", stakedLP.getAddress());

        assertEquals(stakedLP.getAddress(), liquidity.call("getStakedLP"));
    }

    @Test
    void getLiquidityContractBalance() {

        // Set mock balances for liquidity contract.
        Account liquidityAccount = liquidity.getAccount();
        liquidityAccount.addBalance("sicx", BigInteger.valueOf(67));
        liquidityAccount.addBalance("bnusd", BigInteger.valueOf(65));

        System.out.println("sicx address: " + sicx.getAddress());
        System.out.println("bnusd address: " + bnusd.getAddress());

        // Transfer sicx and bnusd to liquidity contract.
        sicx.invoke(owner, "transfer", liquidity.getAddress(), liquidityAccount.getBalance("sicx"), new byte[0]);
        bnusd.invoke(owner, "transfer", liquidity.getAddress(), liquidityAccount.getBalance("bnusd"), new byte[0]);
        
        BigInteger sicxBalance = (BigInteger) sicx.call("balanceOf", liquidity.getAddress());
        System.out.println(sicxBalance);

        BigInteger bnusdBalance = (BigInteger) bnusd.call("balanceOf", liquidity.getAddress());
        System.out.println(bnusdBalance);

        // Act.
        Map<Address, BigInteger> balances = (Map<Address, BigInteger>) liquidity.call("getTokenBalances");
        System.out.println(balances);
        System.out.flush();

        //// Assert.
        //assertEquals(liquidityAccount.getBalance("sicx"), balances.get(sicx.getAddress()));
        //assertEquals(liquidityAccount.getBalance("bnusd"), balances.get(bnusd.getAddress()));
    }

    private Score deployIRC2Basic(Account owner, String name, String symbol, int decimals, BigInteger initialSupply) throws Exception {
        return sm.deploy(owner, IRC2BasicToken.class, name, symbol, decimals, initialSupply);
   }
}
