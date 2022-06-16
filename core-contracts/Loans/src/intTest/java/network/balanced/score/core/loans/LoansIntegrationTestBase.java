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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import foundation.icon.icx.KeyWallet;
import foundation.icon.jsonrpc.model.TransactionResult;

import static  network.balanced.score.lib.utils.Constants.*;
import static  network.balanced.score.lib.test.integration.BalancedUtils.*;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import score.UserRevertedException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.createWalletWithBalance;
import static org.junit.jupiter.api.Assertions.*;

class LoansIntegrationTestBase extends LoansIntegrationTest {
    @BeforeAll
    public static void contractcSetup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        owner = balanced.ownerClient;
        reader = balanced.newClient(BigInteger.ZERO);
        governanceDebt = reader.loans.getBalanceAndSupply("Loans", balanced.governance._address()).get("_balance");
        
        LoansIntegrationTest.setup();
        
    }
   
}
