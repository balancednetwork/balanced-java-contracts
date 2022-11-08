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

package network.balanced.score.core.loans.utils;

import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.utils.LoansConstants.Standings;
import static network.balanced.score.core.loans.utils.LoansConstants.StandingsMap;

public class Standing {
    public BigInteger totalDebt;
    public BigInteger collateral;
    public BigInteger ratio;
    public Standings standing;

    public Map<String, Object> toMap() {
        Map<String, Object> standingData = new HashMap<>();
        standingData.put("collateral", collateral);
        standingData.put("debt", totalDebt);
        standingData.put("ratio", ratio);
        standingData.put("standing", StandingsMap.get(standing));

        return standingData;
    }
}