/*
 * Copyright (c) 2021 Balanced.network.
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

package network.balanced.score.tokens.db;

import network.balanced.score.tokens.utils.UnsignedBigInteger;
import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class LockedBalance {
    public BigInteger amount;
    public UnsignedBigInteger end;

    public LockedBalance(BigInteger amount, BigInteger end) {
        this(amount, new UnsignedBigInteger(end));
    }

    public LockedBalance(BigInteger amount, UnsignedBigInteger end) {
        this.amount = amount;
        this.end = end;
    }

    public LockedBalance() {
        this(BigInteger.ZERO, BigInteger.ZERO);
    }

    public LockedBalance newLockedBalance() {
        return new LockedBalance(this.amount, this.end);
    }

    public BigInteger getEnd() {
        return this.end.toBigInteger();
    }

    public static void writeObject(ObjectWriter writer, LockedBalance obj) {
        obj.writeObject(writer);
    }

    public static LockedBalance readObject(ObjectReader reader) {
        LockedBalance obj = new LockedBalance();
        reader.beginList();
        obj.amount = reader.readBigInteger();
        obj.end = new UnsignedBigInteger(reader.readBigInteger());
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.amount);
        writer.write(this.end.toBigInteger());
        writer.end();
    }

    public static LockedBalance fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return LockedBalance.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        LockedBalance.writeObject(writer, this);
        return writer.toByteArray();
    }
}
