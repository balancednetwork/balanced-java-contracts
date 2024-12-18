/*
 * Copyright (c) 2023-2023 Balanced.network.
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

package network.balanced.score.core.governance.utils;

import score.Address;
import score.Context;

import java.math.BigInteger;

public class EventLogger {
    public static void VoteCast(String vote_name, boolean vote, Address voter, BigInteger stake, BigInteger total_for,
                                BigInteger total_against) {
        Context.logEvent(
                new Object[]{"VoteCast(str,bool,Address,int,int,int)", vote_name, vote},
                new Object[]{voter, stake, total_for, total_against}
        );
    }

    public static void VoteCastV2(String vote_name, boolean vote, String voter, BigInteger stake, BigInteger total_for,
                                BigInteger total_against) {
        Context.logEvent(
                new Object[]{"VoteCast(str,bool,String,int,int,int)", vote_name, vote},
                new Object[]{voter, stake, total_for, total_against}
        );
    }
}
