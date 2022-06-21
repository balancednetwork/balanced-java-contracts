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

package network.balanced.score.core.balancedoracle;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import network.balanced.score.lib.structs.Disbursement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import score.Address;
import score.Context;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.balanced.score.lib.test.UnitTest;
import network.balanced.score.lib.test.mock.MockContract;
import network.balanced.score.lib.interfaces.*;

class BalancedOracleTestSetup extends BalancedOracleTestBase {
    @BeforeEach
    public void setupContractsAndWallets() throws Exception {
        setup();
    }

    @Test
    void testAdmin() {
        testAdmin(balancedOracle, governance, adminAccount);
    }

    @Test
    void testGovernance() {
        testGovernance(balancedOracle, governance, owner);
    }

    @Test
    void setGetOracle() {
        testContractSettersAndGetters(balancedOracle, governance, adminAccount, "setOracle", oracle.getAddress(), "getOracle");
    }

    @Test
    void setGetDex() {
        testContractSettersAndGetters(balancedOracle, governance, adminAccount, "setDex", dex.getAddress(), "getDex");
    }

    @Test
    void setGetStaking() {
        testContractSettersAndGetters(balancedOracle, governance, adminAccount, "setStaking", staking.getAddress(), "getStaking");
    }

    @Test
    void addRemoveDexPricedAssets() {
        // Arrange
        String balnSymbol = "BALN";
        String ommSymbol = "OMM";

        BigInteger balnBnusdPoolID = BigInteger.valueOf(3);
        BigInteger ommBnusdPoolID = BigInteger.valueOf(4);

        // Act
        balancedOracle.invoke(owner, "addDexPricedAsset", balnSymbol, balnBnusdPoolID);
        balancedOracle.invoke(owner, "addDexPricedAsset", ommSymbol, ommBnusdPoolID);

        // Assert
        assertEquals(balnBnusdPoolID, balancedOracle.call("getAssetBnusdPoolId", balnSymbol));
        assertEquals(ommBnusdPoolID, balancedOracle.call("getAssetBnusdPoolId", ommSymbol));

        // Act
        balancedOracle.invoke(owner, "removeDexPricedAsset", balnSymbol);

        // Assert
        String expectedErrorMessage = balnSymbol + " is not listed as a dex priced asset";
        Executable getBalnPoolID = () -> balancedOracle.call("getAssetBnusdPoolId", balnSymbol);
        expectErrorMessage(getBalnPoolID, expectedErrorMessage);
        
        assertEquals(ommBnusdPoolID, balancedOracle.call("getAssetBnusdPoolId", ommSymbol));

    }
}

