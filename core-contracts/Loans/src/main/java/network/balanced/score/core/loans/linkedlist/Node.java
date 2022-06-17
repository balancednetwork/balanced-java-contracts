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

package network.balanced.score.core.loans.linkedlist;

import score.Context;
import score.VarDB;

import java.math.BigInteger;

public class Node {

    private final String _NAME = "_Node";
    private final String name;
    private final VarDB<String> nodeData;
    private String dataString;
    private BigInteger value;
    private int next;
    private int prev;

    Node(String dbName) {
        name = dbName + _NAME;
        nodeData = Context.newVarDB(name + "_node_data", String.class);
        initialize();
    }

    @SuppressWarnings("unchecked")
    Node(String dbName, String key) {
        name = dbName + _NAME;
        nodeData = (VarDB<String>) Context.newBranchDB(name + "_node_data", String.class).at(key);
        initialize();
    }

    private void initialize() {
        dataString = nodeData.get();
        if (dataString == null || dataString.isEmpty()) {
            value = BigInteger.ZERO;
            next = 0;
            prev = 0;
            return;
        }

        int delimiter1 = dataString.indexOf("|");
        int delimiter2 = dataString.lastIndexOf("|");
        value = new BigInteger(dataString.substring(0, delimiter1));
        next = Integer.parseInt(dataString.substring(delimiter1 + 1, delimiter2));
        prev = Integer.parseInt(dataString.substring(delimiter2 + 1));
    }

    void repack() {
        String newData = value.toString() + "|" + next + "|" + prev;
        if (!newData.equals(dataString)) {
            dataString = newData;
            nodeData.set(newData);
        }
    }

    void delete() {
        value = BigInteger.ZERO;
        next = 0;
        prev = 0;
        dataString = null;
        nodeData.set(null);
    }

    boolean exists() {
        return dataString != null && !dataString.isEmpty();
    }

    BigInteger getValue() {
        return value;
    }

    void setValue(BigInteger value) {
        this.value = value;
    }

    int getNext() {
        return next;
    }

    void setNext(int nextId) {
        next = nextId;
    }

    int getPrev() {
        return prev;
    }

    void setPrev(int prevId) {
        prev = prevId;
    }
}