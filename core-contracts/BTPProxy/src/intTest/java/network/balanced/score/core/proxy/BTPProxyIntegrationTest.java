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

package network.balanced.score.core.proxy;

import foundation.icon.icx.Wallet;
import network.balanced.score.lib.interfaces.BTPProxyScoreClient;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.evm.Configuration;
import org.web3j.evm.ConsoleDebugTracer;
import org.web3j.evm.EmbeddedWeb3jService;

import network.balanced.score.btp.MockBTP;
import network.balanced.score.btp.Relay;
import network.balanced.score.btp.ReqID;
import network.balanced.score.btp.icon.BTPAddress;
import network.balanced.solidity.proxy.Proxy;


import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.lib.test.integration.ScoreIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static network.balanced.score.lib.test.integration.Env.getDefaultChain;

class BTPProxyIntegrationTest {

    protected static BTPProxyScoreClient proxy_icon;
    protected static Proxy proxy_eth;
    @BeforeAll
    static void setup() throws Exception {
        Wallet owner = createWalletWithBalance(BigInteger.TEN.pow(24));
        Credentials credentials = Credentials.create("123");
        Configuration configuration = new Configuration(new org.web3j.abi.datatypes.Address(credentials.getAddress()), 10);
        Web3j web3j = Web3j.build(new EmbeddedWeb3jService(configuration));
        TransactionManager txManager = new RawTransactionManager(web3j, credentials, 40, 1000);
        MockBTP.configureBTP_ICON(owner, getDefaultChain().getEndpointURL(), getDefaultChain().networkId);
        MockBTP.configureBTP_EVM(web3j, txManager);
        MockBTP.deploy();

        // proxy_icon = new BTPProxyScoreClient(deploy(owner, "BTPProxy", Map.of("_callService", MockBTP.XCall_ICON._address())));
        // proxy_eth = Proxy.deploy(web3j, txManager, new DefaultGasProvider()).send();
        // proxy_eth.initialize(MockBTP.XCall_EVM.getContractAddress()).send();

        // BTPAddress ICONProxyBTPAddress = new BTPAddress("icon", proxy_icon._address().toString());
        // BTPAddress EVMProxyBTPAddress = new BTPAddress("evm", proxy_eth.getContractAddress().toString());
    }

    @Test
    void test() throws Exception {
        // ReqID id = new ReqID();
        // byte[] data = "test".getBytes();

        // Relay.relayToICON(id).accept(proxy_eth.sendMessage(ICONProxyBTPAddress.toString(), data, new byte[0], BigInteger.TEN.pow(18)).send());
        // Relay.executeXCallICON(id);

        // proxy_icon.sendMessage(Relay.relayToEVM(id), BigInteger.TEN.pow(18), EVMProxyBTPAddress.toString(), data, null);
        // Relay.executeXCallEVM(id);
    }
}
