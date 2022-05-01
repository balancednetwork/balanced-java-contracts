package network.balanced.score.core.loans.positions;

import score.Context;
import score.VarDB;
import score.DictDB;
import score.Address;

import java.math.BigInteger;
import java.util.Map;

import network.balanced.score.core.loans.utils.*;
import static network.balanced.score.core.loans.utils.Constants.*;
import network.balanced.score.core.loans.LoansImpl;
import network.balanced.score.core.loans.linkedlist.*;
import network.balanced.score.core.loans.snapshot.*;
import network.balanced.score.core.loans.asset.*;

public class PositionsDB {
    public static final String POSITION_DB_PREFIX  = "position";
    public static final String IDFACTORY = "idfactory";
    public static final String ADDRESSID = "addressid";
    public static final String NONZERO = "nonzero";
    public static final String NEXT_NODE = "next_node";

    public static final IdFactory idFactory = new IdFactory(IDFACTORY);
    public static final DictDB<Address, Integer> addressIds = Context.newDictDB(ADDRESSID, Integer.class);
    public static final VarDB<Integer> nextPositionNode = Context.newVarDB(NEXT_NODE, Integer.class);

    // private static HashMap<Integer, Position> items = new HashMap<Integer, Position>(10);

    public static Position get(int id) {
        Context.require(id != 0,  "That is not a valid key.");
        if (id < 0) {
            id = idFactory.getLastUid() + id + 1;
        }
    
        // if (!items.containsKey(id)) {
        //     Context.require(id <=  idFactory.getLastUid(),  "That is not a valid key.");
        //     items.put(id, new Position(POSITION_DB_PREFIX + "|" + id));
        // }

        // return items.get(id);
        return new Position(POSITION_DB_PREFIX + "|" + id);
    }

    public static Boolean hasPosition(Address address) {
        return addressIds.getOrDefault(address, null) != null;
    }

    public static int size() {
        return idFactory.getLastUid();
    }

    public static LinkedListDB getNonZero() {
        return new LinkedListDB(NONZERO);
    }

    public static void addNonZero(int id) {
        Snapshot currentSnapshot = SnapshotDB.get(BigInteger.valueOf(-1));
        LinkedListDB addToNonZero = currentSnapshot.getAddNonzero();
        LinkedListDB removeFromNonZero = currentSnapshot.getRemoveNonzero();
        if (removeFromNonZero.contains(id)) {
            removeFromNonZero.remove(id);
        } else {
            addToNonZero.append(BigInteger.ZERO, id);
        }
    }

    public static void removeNonZero(int id) {
        Snapshot currentSnapshot = SnapshotDB.get(BigInteger.valueOf(-1));
        LinkedListDB addToNonZero = currentSnapshot.getAddNonzero();
        LinkedListDB removeFromNonZero = currentSnapshot.getRemoveNonzero();
        if (addToNonZero.contains(id)) {
            addToNonZero.remove(id);
        } else {
            removeFromNonZero.append(BigInteger.ZERO, id);
        }
    }

    public static Position getPosition(Address address) {
        int id = addressIds.getOrDefault(address, 0);
        if (id == 0) {
            return newPosition(address);
        }
        return get(id);
    }

    public static Map<String, Object> listPosition(Address address) {
        int id = addressIds.getOrDefault(address, 0);
        if (id == 0) {
            return Map.of("message", "That address has no outstanding loans or deposited collateral.");
        }
        return get(id).toMap(BigInteger.valueOf(-1));
    }

    private static Position newPosition(Address address) {
        int id = idFactory.getUid();
        addressIds.set(address, id);
        Position position = get(id);
        BigInteger snapshotIndex = LoansImpl._getDay();
        position.snaps.add(snapshotIndex);
        position.id.set(id);
        position.created.set(BigInteger.valueOf(Context.getBlockTimestamp()));
        position.address.set(address);
        position.assets.at(snapshotIndex).set("sICX", BigInteger.ZERO);

        // items.put(id, position);
        return position;
    }

    public static void takeSnapshot() {
        Snapshot snapshot = SnapshotDB.get(BigInteger.valueOf(-1));
        for (int i = 0; i < AssetDB.assetSymbols.size(); i++) {
            String symbol = AssetDB.assetSymbols.get(i);
            Asset asset = AssetDB.get(symbol);
            if (asset.isActive()) {
                snapshot.prices.set(symbol, asset.priceInLoop());
            }
        }

        snapshot.time.set(BigInteger.valueOf(Context.getBlockTimestamp()));
        if (LoansImpl._getDay() != LoansImpl.continuousRewardDay.get()) {
            SnapshotDB.startNewSnapshot();
        }
    }

    public static Boolean calculateSnapshot(BigInteger day, int batchSize) {
        Context.require(day.compareTo(LoansImpl.continuousRewardDay.get()) < 0, "The continuous rewards is already active.");
        Snapshot snapshot = SnapshotDB.get(day);
        BigInteger snapshotId = snapshot.day.get();
        if (snapshotId.compareTo(day) < 0) {
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
            int iterations = LoansImpl.snapBatchSize.get();
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

        int index = snapshot.preComputeIndex.getOrDefault(0);
        int totalNonZero = nonZero.size();
        nextNode = nextPositionNode.getOrDefault(0);
        if (nextNode == 0) {
            nextNode = nonZero.getHeadId();
        }

        int remaning = totalNonZero - index;
        BigInteger batchMiningDebt = BigInteger.ZERO;
        int loops = Math.min(remaning, batchSize);

        for (int i = 0; i < loops; i++) {
            int accountId = nextNode;
            Position position = get(accountId);
            if (snapshotId.compareTo(position.snaps.get(0)) >= 0) {
                Standings standing = position.updateStanding(snapshotId);
                if (standing == Standings.MINING) {
                    snapshot.mining.add(accountId);
                    batchMiningDebt = batchMiningDebt.add(snapshot.positionStates.at(accountId).get("total_debt"));
                }
            }
            index++;
            if (accountId == nonZero.getTailId()) {
                nextNode = 0;
            } else {
                nextNode = nonZero.next(nextNode);
            }
        }

        snapshot.totalMiningDebt.set(snapshot.totalMiningDebt.getOrDefault(BigInteger.ZERO).add(batchMiningDebt));
        snapshot.preComputeIndex.set(index);
        nextPositionNode.set(nextNode);

        if (totalNonZero == index) {
            return true;
        }

        return false;
    }
}