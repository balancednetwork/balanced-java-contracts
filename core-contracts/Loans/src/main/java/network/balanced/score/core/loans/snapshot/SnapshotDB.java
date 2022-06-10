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

import network.balanced.score.core.loans.LoansImpl;
import score.ArrayDB;
import score.Context;

public class SnapshotDB {

    private static final String TAG = "BalancedLoansSnapshots";
    private static final String SNAP_DB_PREFIX = "snaps";
    private static final ArrayDB<Integer> indexes = Context.newArrayDB("indexes", Integer.class);

    public static Snapshot get(Integer day) {
        int inputDay = day;
        int index = getSnapshotId(day);
        if (day < 0) {
            day = index;
        }
        Context.require(index >= indexes.get(0) && index <= indexes.get(indexes.size() - 1),
                TAG + ": No snapshot exists for " + day + ", input_day: " + inputDay + ".");
        return getSnapshot(index);
    }

    public static Integer size() {
        return indexes.get(indexes.size() - 1) - (indexes.get(0));
    }

    private static Snapshot getSnapshot(Integer index) {
        return new Snapshot(SNAP_DB_PREFIX + "|" + index);
    }

    public static Integer getLastSnapshotIndex() {
        return indexes.get(indexes.size() - 1);
    }

    public static Integer getSnapshotId(Integer day) {
        if (day < 0) {
            int index = day + indexes.size();
            Context.require(index >= 0, TAG + ": Snapshot index " + day + " out of range.");
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

        if (indexes.get(0).equals(day)) {
            return day;
        } else if (low == 0) {
            return -1;
        }

        return indexes.get(low - 1);    
    }

    public static void startNewSnapshot() {
        int day = LoansImpl._getDay().intValue();

        Context.require(indexes.size() == 0 || day > getLastSnapshotIndex(), TAG + ": New snapshot called for a day " +
                "less than the previous snapshot.");
        indexes.add(day);
        Snapshot snapshot = getSnapshot(day);
        snapshot.setDay(day);
    }
}