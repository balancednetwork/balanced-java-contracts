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

package network.balanced.score.core.loans.positions;

import network.balanced.score.core.loans.utils.IdFactory;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.positions.Position.TAG;
import static network.balanced.score.core.loans.utils.LoansConstants.*;

public class PositionsDB {
    private static final String POSITION_DB_PREFIX = "position";

    private static final String ID_FACTORY = "idfactory";
    private static final String ADDRESS_ID = "addressid";

    private static final IdFactory idFactory = new IdFactory(ID_FACTORY);
    private static final DictDB<Address, Integer> addressIds = Context.newDictDB(ADDRESS_ID, Integer.class);

    public static Integer getAddressIds(Address _owner) {
        return addressIds.getOrDefault(_owner, 0);
    }

    public static Position get(Integer id) {
        int lastUid = idFactory.getLastUid();
        if (id < 0) {
            id = lastUid + id + 1;
        }
        
        Context.require(id >= 1, TAG + ": That is not a valid key.");
        Context.require(id <= lastUid, TAG + ": That key does not exist yet.");
        return new Position(POSITION_DB_PREFIX + "|" + id);
    }

    public static int size() {
        return idFactory.getLastUid();
    }

    public static Boolean hasPosition(Address address) {
        return getAddressIds(address) != 0;
    }

    public static Map<String, Object> listPosition(Address _owner) {
        int id = getAddressIds(_owner);
        if (id == 0) {
            return Map.of("message", "That address has no outstanding loans or deposited collateral.");
        }
        return get(id).toMap(-1);
    }

    public static Position getPosition(Address owner) {
        return  getPosition(owner, false);
    }
    
    public static Position getPosition(Address owner, boolean readOnly) {
        int id = getAddressIds(owner);
        if (id == 0) {
            if (readOnly) {
                Context.revert(TAG + ": Address " + owner + " has no open position");
            }
            return newPosition(owner);
        }

        return get(id);
    }

    private static Position newPosition(Address owner) {
        Context.require(getAddressIds(owner) == 0, TAG + ": A position already exists for that address");
        int id = idFactory.getUid();
        addressIds.set(owner, id);
        Position newPosition = get(id);
        newPosition.setId(id);
        newPosition.setCreated(BigInteger.valueOf(Context.getBlockTimestamp()));
        newPosition.setAddress(owner);

        newPosition.setCollateral(SICX_SYMBOL, BigInteger.ZERO);

        return newPosition;
    }
}