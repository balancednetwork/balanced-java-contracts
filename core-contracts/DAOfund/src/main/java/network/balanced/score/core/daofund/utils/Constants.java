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

package network.balanced.score.core.daofund.utils;

import java.util.Map;

public class Constants {
    public static final String GOVERNANCE = "governance";
    public static final String ADMIN = "admin";
    public static final String LOANS_SCORE = "loans_score";
    public static final String SYMBOL = "symbol";
    public static final String ADDRESS = "address";
    public static final String FUND = "fund";
    public static final String AWARDS = "awards";

    public static final Map<String, String> TOKEN_ADDRESSES = Map.of(
            "sICX", "cx2609b924e33ef00b648a409245c7ea394c467824",
            "bnUSD", "cx88fd7df7ddff82f7cc735c871dc519838cb235bb",
            "BALN", "cxf61cd5a45dc9f91c15aa65831a30a90d59a09619",
            "USDS", "cxbb2871f468a3008f80b08fdde5b8b951583acf06",
            "IUSDC", "cxae3034235540b924dfcc1b45836c293dcc82bfb7",
            "OMM", "cx1a29259a59f463a67bb2ef84398b30ca56b5830a");
}
