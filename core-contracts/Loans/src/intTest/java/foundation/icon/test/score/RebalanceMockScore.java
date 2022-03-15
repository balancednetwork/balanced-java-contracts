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

package network.balanced.test.score;

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

import network.balanced.test.contracts.base.RebalancingScore;

import java.io.IOException;
import java.math.BigInteger;

import static network.balanced.test.Env.LOG;

public class RebalanceMockScore extends RebalancingScore {

    public static RebalanceMockScore deploy(TransactionHandler txHandler, Wallet wallet, Address loansAddress)
    throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.info("DeployRebalanceMock");
        RpcObject params = new RpcObject.Builder()
                .put("_loansAddress", new RpcValue(loansAddress))
                .build();
        Score score = txHandler.deploy(wallet, getFilePath("RebalanceMock"), params);
        return new RebalanceMockScore(score);
    }

    public RebalanceMockScore(Score other) {
        super(other);
    }


    public TransactionResult raisePrice(Wallet fromWallet, BigInteger _total_tokens_required) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_total_tokens_required", new RpcValue(_total_tokens_required))
            .build();
        return invokeAndWaitResult(fromWallet, "raisePrice", params);
    }

    public TransactionResult lowerPrice(Wallet fromWallet, BigInteger _total_tokens_required) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            .put("_total_tokens_required", new RpcValue(_total_tokens_required))
            .build();
        return invokeAndWaitResult(fromWallet, "lowerPrice", params);
    }
}