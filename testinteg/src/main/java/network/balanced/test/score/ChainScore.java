/*
 * Copyright 2020 ICONLOOP Inc.
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
import network.balanced.test.Constants;
import network.balanced.test.TransactionHandler;
import network.balanced.test.ResultTimeoutException;

import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

public class ChainScore extends Score {

    public ChainScore(TransactionHandler txHandler) {
        super(txHandler, Constants.ZERO_ADDRESS);
    }

    public TransactionResult registerPrep(Wallet prepWallet) throws IOException, ResultTimeoutException {
        RpcObject params = new RpcObject.Builder()
            //TODO use a balanced themed node setup 
            .put("name", new RpcValue("kokoa node"))
            .put("email", new RpcValue("kokoa@example.com"))
            .put("country", new RpcValue("USA"))
            .put("city", new RpcValue("New York"))
            .put("website", new RpcValue("https://icon.kokoa.com"))
            .put("details", new RpcValue("https://icon.kokoa.com/json/details.json"))
            .put("p2pEndpoint", new RpcValue("localhost:9082"))
            .build();
        //todo get fee from chain
        return invokeAndWaitResult(prepWallet, "registerPRep", params, BigInteger.valueOf(2000).multiply(BigInteger.TEN.pow(18)), null);
    }   

    public BigInteger getStepPrice() throws IOException {
        return call("getStepPrice", null).asInteger();
    }
}
