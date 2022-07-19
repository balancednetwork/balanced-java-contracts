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

import score.Context;

import static network.balanced.score.core.loans.LoansImpl.TAG;
import static network.balanced.score.core.loans.LoansVariables.loansOn;


public class Checks {
    public static void loansOn() {
        Context.require(loansOn.get(), TAG + ": Balanced Loans SCORE is not active.");
    }
}
