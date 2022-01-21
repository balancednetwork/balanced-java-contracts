package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.Address;
import scorex.util.HashMap;
import java.util.Map;
import java.math.BigInteger;

import score.ArrayDB;
import score.DictDB;
import score.BranchDB;

public class SnapshotDB {
    private static final String SNAP_DB_PREFIX = "snaps";
    public static ArrayDB<Integer> indexes = Context.newArrayDB("indexes", Integer.class);
    // private static HashMap<Integer, Snapshot> items = new HashMap<Integer, Snapshot>(10);

    public static Snapshot get(int day) {
        int inputDay = day;
        int index = getSnapshotId(day);
        if (day < 0) {
            day = index;
        }
        Context.require(index >= indexes.get(0) && index < indexes.get(indexes.size() -1) + 1, "no snapshot exists for " + day + ", input_day: " + inputDay + ".");

        // if (!items.containsKey(day)) {
        //     return getSnapshot(day, index);
        // }
        // return items.get(day);
        return getSnapshot(day, index);
    }

    public static int size() {
        return indexes.get(indexes.size() -1) - indexes.get(0);
    }

    private static Snapshot getSnapshot(int day, int index) {
        Snapshot snapshot = new Snapshot(SNAP_DB_PREFIX + "|" + index);
 
        // items.put(day, snapshot);
        return snapshot;
    }

    public static int getLastSnapshotIndex() {
        return indexes.get(indexes.size() -1);
    }

    public static int getSnapshotId(int day) {
        if (day < 0) {
            int index = day + indexes.size();
            Context.require(index >= 0, "Snapshot index " + day + " out of range.");
            return indexes.get(index);
        }

        int low = 0;
        int high = indexes.size();
        int middle;
        while (low < high) {
            middle = (low + high) / 2;
            if (indexes.get(middle) > day) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }

        if (indexes.get(0) == day) {
            return day;
        } else if (low == 0) {
            return -1;
        }

        return indexes.get(low - 1);    
    }

    public static void startNewSnapshot() {
        int day = Loans._getDay();

        Context.require(indexes.size() == 0 || day > getLastSnapshotIndex(), "New snapshot called for a day less than the previous snapshot.");
        indexes.add(day);
        Snapshot snapshot = getSnapshot(day, day);
        snapshot.day.set(day);
    }
}