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

package network.balanced.score.core.governance.deploymentTester;

import score.Context;
import score.VarDB;
import score.annotation.External;

public class DeploymentTester {
    public static final String name = "Balanced Deployment Test Contract";
    public static final VarDB<String> testValue = Context.newVarDB("testValue", String.class);
    public static final VarDB<String> testValueV2 = Context.newVarDB("testValueV2", String.class);

    public DeploymentTester(String value) {
        testValue.set(value);
    }

    @External(readonly = true)
    public String name() {
        return name;
    }

    @External(readonly = true)
    public String getValue() {
        return testValue.get();
    }

    //################################
    // V2 ONLY
    //################################
    @External
    public void setValue2(String value) {
        testValueV2.set(value);
    }

    @External(readonly = true)
    public String getValue2() {
        return testValueV2.getOrDefault("notSet");
    }

}
