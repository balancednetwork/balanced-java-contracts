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

package network.balanced.score.core.governance;

public class ProposalStatus {
    public static final int PENDING = 0;
    public static final int ACTIVE = 1;
    public static final int CANCELLED = 2;
    public static final int DEFEATED = 3;
    public static final int SUCCEEDED = 4;
    public static final int NO_QUORUM = 5;
    public static final int EXECUTED = 6;
    public static final int FAILED_EXECUTION = 7;
    public static final String[] STATUS = new String[]{"Pending", "Active", "Cancelled", "Defeated", "Succeeded", "No" +
            " Quorum", "Executed", "Failed Execution"};
}