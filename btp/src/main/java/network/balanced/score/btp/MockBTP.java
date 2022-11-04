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

package network.balanced.score.btp;


import static foundation.icon.score.client.DefaultScoreClient._deploy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.InputStream;
import java.math.BigInteger;

import java.util.Map;

import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import network.balanced.score.btp.icon.CallServiceScoreClient;
import network.balanced.score.btp.icon.MockBMCScoreClient;
import network.balanced.solidity.btp.CallService;
import network.balanced.solidity.btp.MockBMC;

public class MockBTP {
    private static String url;
    private static BigInteger nid;
    private static Web3j web3j;
    private static Wallet owner;
    private static TransactionManager tm;
    private static String xCallPath = "/contracts/xcall-0.1.0-optimized.jar";
    private static String BMCPath = "/contracts/bmc-mock-0.1.0-optimized.jar";

    public static MockBMC BMC_EVM;
    public static CallService XCall_EVM;

    public static MockBMCScoreClient BMC_ICON;
    public static CallServiceScoreClient XCall_ICON;

    public static void configureBTP_ICON(Wallet owner, String url, BigInteger nid) {
        MockBTP.url = url;
        MockBTP.nid = nid;
        MockBTP.owner = owner;
    }

    public static void configureBTP_EVM(Web3j web3j, TransactionManager tm) {
        MockBTP.web3j = web3j;
        MockBTP.tm = tm;
    }

    public static void deploy() {
        assertDoesNotThrow(() ->deployBTPJava());
        assertDoesNotThrow(() -> deployBTPEVM());
    }

    private static void deployBTPJava() throws Exception{
        InputStream bmc = MockBTP.class.getResourceAsStream(BMCPath);
        BMC_ICON = new MockBMCScoreClient(deployJava(bmc.readAllBytes(), null));

        InputStream xcall = MockBTP.class.getResourceAsStream(xCallPath);
        XCall_ICON = new CallServiceScoreClient(deployJava(xcall.readAllBytes(), Map.of("_bmc", BMC_ICON._address())));
    }

    private static DefaultScoreClient deployJava(byte[] content, Map<String, Object> params) {
        DefaultScoreClient client = _deploy(url,
            nid,
            owner,
            content,
            params);
        return client;
    }

    private static void deployBTPEVM() throws Exception {
        BMC_EVM = MockBMC.deploy(web3j, tm, new DefaultGasProvider(), "evm").send();
        XCall_EVM = CallService.deploy(web3j, tm, new DefaultGasProvider()).send();
        XCall_EVM.initialize(BMC_EVM.getContractAddress()).send();
    }


}