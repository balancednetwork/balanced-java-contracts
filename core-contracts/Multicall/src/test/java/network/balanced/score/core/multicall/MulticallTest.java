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

package network.balanced.score.core.multicall;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import score.Address;


import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


public class MulticallTest extends TestBase {

    public static final ServiceManager sm = getServiceManager();
    public static final Account owner = sm.createAccount();

    private Score multicallScore;
    private Score dexMock;

    private DexMock dexSpy;

    private void expectErrorMessage(Executable contractCall, String expectedErrorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(expectedErrorMessage, e.getMessage());
    }

    @BeforeEach
    public void setup() throws Exception {
        multicallScore = sm.deploy(owner, Multicall.class);
        assert (multicallScore.getAddress().isContract());

        dexMock = sm.deploy(owner, DexMock.class);
        assert (dexMock.getAddress().isContract());

        dexSpy = (DexMock) spy(dexMock.getInstance());
        dexMock.setInstance(dexSpy);
    }


    @Test
    void name() {
        String contractName = "Balanced Multicall";
        assertEquals(contractName, multicallScore.call("name"));
    }

    @Test
    void setAndGetDex() {
        Executable setDexNotFromOwner = () -> multicallScore.invoke(sm.createAccount(), "setDexAddress",
                dexMock.getAddress());
        String expectedErrorMessage = "Reverted(0): Multicall: Caller is not the owner";
        expectErrorMessage(setDexNotFromOwner, expectedErrorMessage);

        multicallScore.invoke(owner, "setDexAddress", dexMock.getAddress());
        Address actualDex = (Address) multicallScore.call("getDexAddress");
        assertEquals(dexMock.getAddress(), actualDex);
    }

}
