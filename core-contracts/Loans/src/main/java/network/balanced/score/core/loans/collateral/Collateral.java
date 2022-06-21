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

package network.balanced.score.core.loans.collateral;

import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.BranchDB;
import score.Context;
import score.VarDB;
import scorex.util.HashMap;

import java.util.Map;

public class Collateral {

    private final BranchDB<String, VarDB<Address>> assetAddress = Context.newBranchDB("address", Address.class);
    private final BranchDB<String, VarDB<Boolean>> active = Context.newBranchDB("active", Boolean.class);

    private final String dbKey;

    Collateral(String key) {
        dbKey = key;
    }

    public Address getAssetAddress() {
        return assetAddress.at(dbKey).get();
    }

    public void setActive(Boolean active) {
        this.active.at(dbKey).set(active);
    }

    public boolean isActive() {
        return active.at(dbKey).getOrDefault(false);
    }

    void setCollateral(Address assetAddress, Boolean active) {
        this.assetAddress.at(dbKey).set(assetAddress);
        this.active.at(dbKey).set(active);
    }

    Map<String, Object> toMap() {
        Address assetAddress = this.assetAddress.at(dbKey).get();
        Token tokenContract = new Token(assetAddress);

        Map<String, Object> AssetDetails = new HashMap<>();

        AssetDetails.put("symbol", tokenContract.symbol());
        AssetDetails.put("address", assetAddress);
        AssetDetails.put("peg", tokenContract.getPeg());
        AssetDetails.put("active", isActive());
        AssetDetails.put("total_supply", tokenContract.totalSupply());

        return AssetDetails;
    }
}