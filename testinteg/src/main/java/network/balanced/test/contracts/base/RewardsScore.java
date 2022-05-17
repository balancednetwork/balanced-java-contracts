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

public class RewardsScore extends Score {
    private static final String PYTHON_PATH = "../../testinteg/src/main/java/network/balanced/test/contracts/base/pythonContracts/rewards.zip";

    public static RewardsScore deploy(TransactionHandler txHandler, Wallet wallet, Address admin) 
        throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("Deploy Rewards");
        RpcObject params = new RpcObject.Builder()
            .put("_governance", new RpcValue(admin))
            .build();
        Score score = txHandler.deploy(wallet, PYTHON_PATH, params);
        return new RewardsScore(score);
    }

    public RewardsScore(Score other) {
        super(other);
    }
}