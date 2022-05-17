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

package network.balanced.score.core.loans.snapshot;

import score.Context;
import score.ArrayDB;

import java.math.BigInteger;

import network.balanced.score.core.loans.LoansImpl;

public class SnapshotDB {
    private static final String SNAP_DB_PREFIX = "snaps";
    private static final ArrayDB<BigInteger> indexes = Context.newArrayDB("indexes", BigInteger.class);
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

        // items.put(day, snapshot);
        return new Snapshot(SNAP_DB_PREFIX + "|" + index);
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

        if (indexes.get(0).equals(day)) {
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