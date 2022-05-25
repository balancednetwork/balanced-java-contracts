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

package network.balanced.score.core.loans.test.score;

import foundation.icon.icx.Wallet;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import network.balanced.test.ResultTimeoutException;
import network.balanced.test.TransactionFailureException;
import network.balanced.test.TransactionHandler;
import network.balanced.test.contracts.base.Governance;
import network.balanced.test.score.Score;

import java.io.IOException;

import static network.balanced.test.Env.LOG;

public class GovernanceInTest extends Governance {
    public static GovernanceInTest deploy(TransactionHandler txHandler, Wallet wallet)
        throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Deploy Governance");
        RpcObject params = new RpcObject.Builder()
            .build();
        Score score = txHandler.deploy(wallet, PYTHON_PATH, params);
        return new GovernanceInTest(score);
    }

    public GovernanceInTest(Score other) {
        super(other);
    }

    @Override
    protected void deployLoans(TransactionHandler txHandler, Wallet adminWallet) throws Exception {
        loans = LoansScoreInTest.deploy(txHandler, adminWallet, getAddress());
    }
}