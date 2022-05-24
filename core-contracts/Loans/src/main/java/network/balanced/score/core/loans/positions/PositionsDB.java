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

import network.balanced.score.core.loans.LoansImpl;
import network.balanced.score.core.loans.LoansVariables;
import network.balanced.score.core.loans.asset.Asset;
import network.balanced.score.core.loans.asset.AssetDB;
import network.balanced.score.core.loans.linkedlist.LinkedListDB;
import network.balanced.score.core.loans.snapshot.Snapshot;
import network.balanced.score.core.loans.snapshot.SnapshotDB;
import network.balanced.score.core.loans.utils.IdFactory;
import network.balanced.score.core.loans.utils.Token;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.loans.LoansVariables.snapBatchSize;
import static network.balanced.score.core.loans.positions.Position.TAG;
import static network.balanced.score.core.loans.utils.Checks.isBeforeContinuousRewardDay;
import static network.balanced.score.core.loans.utils.LoansConstants.*;

public class PositionsDB {
    private static final String POSITION_DB_PREFIX = "position";

    private static final String ID_FACTORY = "idfactory";
    private static final String ADDRESS_ID = "addressid";
    private static final String NONZERO = "nonzero";
    private static final String NEXT_NODE = "next_node";

    private static final IdFactory idFactory = new IdFactory(ID_FACTORY);
    private static final DictDB<Address, Integer> addressIds = Context.newDictDB(ADDRESS_ID, Integer.class);
    private static final VarDB<Integer> nextPositionNode = Context.newVarDB(NEXT_NODE, Integer.class);

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

    public static LinkedListDB getNonZero() {
        return new LinkedListDB(NONZERO);
    }

    public static void addNonZero(int id) {
        Snapshot currentSnapshot = SnapshotDB.get(-1);
        LinkedListDB addToNonZero = currentSnapshot.getAddNonzero();
        LinkedListDB removeFromNonZero = currentSnapshot.getRemoveNonzero();
        if (removeFromNonZero.contains(id)) {
            removeFromNonZero.remove(id);
        } else {
            addToNonZero.append(BigInteger.ZERO, id);
        }
    }

    public static void removeNonZero(int id) {
        Snapshot currentSnapshot = SnapshotDB.get(-1);
        LinkedListDB addToNonZero = currentSnapshot.getAddNonzero();
        LinkedListDB removeFromNonZero = currentSnapshot.getRemoveNonzero();
        if (addToNonZero.contains(id)) {
            addToNonZero.remove(id);
        } else {
            removeFromNonZero.append(BigInteger.ZERO, id);
        }
    }

    public static Position getPosition(Address owner) {
        int id = getAddressIds(owner);
        if (id == 0) {
            return newPosition(owner);
        }
        return get(id);
    }

    private static Position newPosition(Address owner) {
        Context.require(getAddressIds(owner) == 0, TAG + ": A position already exists for that address");
        int id = idFactory.getUid();
        addressIds.set(owner, id);
        Position newPosition = get(id);
        BigInteger snapshotIndex = LoansImpl._getDay();
        newPosition.addSnaps(snapshotIndex.intValue());
        newPosition.setId(id);
        newPosition.setCreated(BigInteger.valueOf(Context.getBlockTimestamp()));
        newPosition.setAddress(owner);

        if (isBeforeContinuousRewardDay()) {
            newPosition.setAssets(snapshotIndex.intValue(), SICX_SYMBOL, BigInteger.ZERO);
        } else {
            newPosition.setCollateralPosition(SICX_SYMBOL, BigInteger.ZERO);
            newPosition.setDataMigrationStatus(SICX_SYMBOL, true);
        }
        return newPosition;
    }

    /**
     * Captures necessary data for the current snapshot in the SnapshotDB, issues a snapshot eventlog, and starts a
     * new snapshot.
     */
    public static void takeSnapshot() {
        Snapshot snapshot = SnapshotDB.get(-1);

        int assetSymbolsCount = AssetDB.assetSymbols.size();
        for (int i = 0; i < assetSymbolsCount; i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.getAsset(symbol);

            Address assetAddress = asset.getAssetAddress();
            Token assetContract = new Token(assetAddress);

            if (asset.isActive()) {
                snapshot.setPrices(symbol, assetContract.priceInLoop());
            }
        }

        snapshot.setSnapshotTime(BigInteger.valueOf(Context.getBlockTimestamp()));
        if (isBeforeContinuousRewardDay()) {
            SnapshotDB.startNewSnapshot();
        }
    }

    /**
     * Iterates once over all positions to calculate their ratios at the end of the snapshot period.
     *
     * @param day Operating day of the snapshot as passed from rewards via the precompute method
     * @param batchSize Number of positions to bring up to date
     * @return True if complete
     */
    public static Boolean calculateSnapshot(BigInteger day, int batchSize) {
        Context.require(isBeforeContinuousRewardDay(day), continuousRewardsErrorMessage);
        Snapshot snapshot = SnapshotDB.get(day.intValue());
        int snapshotId = snapshot.getDay();
        if (snapshotId < day.intValue()) {
            return true;
        }
        
        LinkedListDB addToNonZero = snapshot.getAddNonzero();
        LinkedListDB removeFromNonZero = snapshot.getRemoveNonzero();
        int add = addToNonZero.size();
        int remove = removeFromNonZero.size();

        int nextNode;

        int nonZeroDeltas = add + remove;
        LinkedListDB nonZero = getNonZero();
        if (nonZeroDeltas > 0) {
            int iterations = snapBatchSize.get();
            int loops = Math.min(iterations, remove);
            
            if (remove > 0) {
                nextNode = removeFromNonZero.getHeadId();
                for (int i = 0; i < loops; i++) {
                    nonZero.remove(nextNode);
                    removeFromNonZero.remove(nextNode);
                    nextNode = removeFromNonZero.getHeadId();
                    iterations = iterations - 1;
                }
            }

            if (iterations > 0) {
                loops = Math.min(iterations, add);
                if (add > 0) {
                    nextNode = addToNonZero.getHeadId();
                    for (int i = 0; i < loops; i++) {
                        nonZero.append(BigInteger.ZERO, nextNode);
                        addToNonZero.remove(nextNode);
                        nextNode = addToNonZero.getHeadId();
                    }
                }
            }

            nonZero.serialize();
            return false;
        }

        int index = snapshot.getPreComputeIndex();
        int totalNonZero = nonZero.size();
        nextNode = nextPositionNode.getOrDefault(0);
        if (nextNode == 0) {
            nextNode = nonZero.getHeadId();
        }

        int remaining = totalNonZero - index;
        BigInteger batchMiningDebt = BigInteger.ZERO;
        int loops = Math.min(remaining, batchSize);

        for (int i = 0; i < loops; i++) {
            int accountId = nextNode;
            Position position = get(accountId);
            if (snapshotId >= position.getSnaps(0)) {
                Standings standing = position.updateStanding(snapshotId);
                if (!position.getDataMigrationStatus(BNUSD_SYMBOL)) {
                    BigInteger previousTotalDebt = LoansVariables.totalDebts.getOrDefault(BNUSD_SYMBOL, BigInteger.ZERO);
                    BigInteger debtAmount = position.getAssets(position.getSnapshotId(day.intValue()), BNUSD_SYMBOL);
                    LoansVariables.totalDebts.set(BNUSD_SYMBOL, previousTotalDebt.add(debtAmount));
                    position.setLoansPosition(SICX_SYMBOL, BNUSD_SYMBOL, debtAmount);
                    position.setDataMigrationStatus(BNUSD_SYMBOL, true);
                }

                if (standing == Standings.MINING) {
                    snapshot.addMining(accountId);
                    batchMiningDebt = batchMiningDebt.add(snapshot.getPositionStates(accountId, "total_debt"));
                }
            }
            index++;
            if (accountId == nonZero.getTailId()) {
                nextNode = 0;
            } else {
                nextNode = nonZero.getNextId(nextNode);
            }
        }

        snapshot.setTotalMiningDebt(snapshot.getTotalMiningDebt().add(batchMiningDebt));
        snapshot.setPreComputeIndex(index);
        nextPositionNode.set(nextNode);

        return totalNonZero == index;
    }
}