package network.balanced.score.core.loans.snapshot;

import score.Context;
import score.ArrayDB;

import java.math.BigInteger;

import network.balanced.score.core.loans.LoansImpl;

public class SnapshotDB {
    private static final String SNAP_DB_PREFIX = "snaps";
    public static ArrayDB<BigInteger> indexes = Context.newArrayDB("indexes", BigInteger.class);
    // private static HashMap<Integer, Snapshot> items = new HashMap<Integer, Snapshot>(10);

    public static Snapshot get(BigInteger day) {
        BigInteger inputDay = day;
        BigInteger index = getSnapshotId(day);
        if (day.compareTo(BigInteger.ZERO) < 0) {
            day = index;
        }
        Context.require(index.compareTo(indexes.get(0)) >= 0 && 
                                        index.compareTo(indexes.get(indexes.size() -1).add(BigInteger.ONE)) < 0, 
                                        "no snapshot exists for " + day + ", input_day: " + inputDay + ".");

        // if (!items.containsKey(day)) {
        //     return getSnapshot(day, index);
        // }
        // return items.get(day);
        return getSnapshot(day, index);
    }

    public static BigInteger size() {
        return indexes.get(indexes.size() -1).subtract(indexes.get(0));
    }

    private static Snapshot getSnapshot(BigInteger day, BigInteger index) {
        Snapshot snapshot = new Snapshot(SNAP_DB_PREFIX + "|" + index);
 
        // items.put(day, snapshot);
        return snapshot;
    }

    public static BigInteger getLastSnapshotIndex() {
        return indexes.get(indexes.size() -1);
    }

    public static BigInteger getSnapshotId(BigInteger day) {
        if (day.compareTo(BigInteger.ZERO) < 0) {
            int index = day.intValue() + indexes.size();
            Context.require(index >= 0, "Snapshot index " + day + " out of range.");
            return indexes.get(index);
        }

        int low = 0;
        int high = indexes.size();
        int middle;
        while (low < high) {
            middle = (low + high) / 2;
            if (indexes.get(middle).compareTo(day) > 0) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        if (indexes.get(0) == day) {
            return day;
        } else if (low == 0) {
            return BigInteger.valueOf(-1);
        }

        return indexes.get(low - 1);    
    }

    public static void startNewSnapshot() {
        BigInteger day = LoansImpl._getDay();

        Context.require(indexes.size() == 0 || day.compareTo(getLastSnapshotIndex()) > 0, "New snapshot called for a day less than the previous snapshot.");
        indexes.add(day);
        Snapshot snapshot = getSnapshot(day, day);
        snapshot.day.set(day);
    }
}