/*
 * Copyright (c) 2022 Balanced.network.
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

package network.balanced.score.tokens.sicx;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.Sicx;
import network.balanced.score.lib.interfaces.SicxScoreClient;
import network.balanced.score.lib.test.ScoreIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.utils.Constants.EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SicxMigrationTest implements ScoreIntegrationTest {

    private static final Wallet owner = ScoreIntegrationTest.getOrGenerateWallet(System.getProperties());
    private static final Address ownerAddress = Address.of(owner);

    private static final DefaultScoreClient sicxClient = DefaultScoreClient.of(System.getProperties(), Map.of("_admin"
            , ownerAddress));

    @ScoreClient
    private final Sicx sicxScore = new SicxScoreClient(sicxClient);

    private static final Wallet tester = ScoreIntegrationTest.getOrGenerateWallet(null);
    private static final Address testerAddress = Address.of(tester);

    @Test
    @Order(1)
    void beforeMigration() {
        BigInteger value = BigInteger.valueOf(40).multiply(EXA);
        sicxScore.mint(value, null);
        sicxScore.mintTo(testerAddress, BigInteger.valueOf(20).multiply(EXA), null);

        assertEquals(BigInteger.valueOf(60).multiply(EXA), sicxScore.totalSupply());
        assertEquals(BigInteger.valueOf(20).multiply(EXA), sicxScore.balanceOf(testerAddress));
        assertEquals(BigInteger.valueOf(40).multiply(EXA), sicxScore.balanceOf(ownerAddress));

        // rename the token name on python as old_sICX
        assertEquals("old_sICX", sicxScore.getPeg());
    }

    @Test
    @Order(2)
    void afterMigration() {
        // update sicx contract with java file
        System.setProperty("scoreFilePath", "../../token-contracts/sicx/build/libs/sicx-0.1.0-optimized.jar");
        System.setProperty("isUpdate", "true");
        System.setProperty("address", String.valueOf(sicxClient._address()));
        DefaultScoreClient.of(System.getProperties(), Map.of("_admin", ownerAddress));

        // test data after migration
        assertEquals("sICX", sicxScore.getPeg());

        assertEquals(BigInteger.valueOf(60).multiply(EXA), sicxScore.totalSupply());
        assertEquals(BigInteger.valueOf(20).multiply(EXA), sicxScore.balanceOf(testerAddress));
        assertEquals(BigInteger.valueOf(40).multiply(EXA), sicxScore.balanceOf(ownerAddress));

        // call mint after migration
        sicxScore.mint(BigInteger.valueOf(20).multiply(EXA), null);
        // call mintTo after migration
        sicxScore.mintTo(testerAddress, BigInteger.valueOf(20).multiply(EXA), null);

        // call burn after migration
        sicxScore.burn(BigInteger.TEN.multiply(EXA));
        //call burnFrom after migration
        sicxScore.burnFrom(testerAddress, BigInteger.TEN.multiply(EXA));

        assertEquals(BigInteger.valueOf(80).multiply(EXA), sicxScore.totalSupply());
        assertEquals(BigInteger.valueOf(30).multiply(EXA), sicxScore.balanceOf(testerAddress));
        assertEquals(BigInteger.valueOf(50).multiply(EXA), sicxScore.balanceOf(ownerAddress));
    }
}
