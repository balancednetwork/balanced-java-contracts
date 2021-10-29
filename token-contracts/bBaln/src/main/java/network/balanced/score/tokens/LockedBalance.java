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

package network.balanced.score.tokens;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;

import java.math.BigInteger;
import java.util.concurrent.locks.Lock;

public class LockedBalance {
    public BigInteger amount;
    public BigInteger end;

    public LockedBalance(BigInteger amount, BigInteger end) {
        this.amount = amount;
        this.end = end;
    }

    public LockedBalance() {this(BigInteger.ZERO, BigInteger.ZERO);}

    public byte[] toByteArray() {
        ByteArrayObjectWriter lockedBalanceInBytes = Context.newByteArrayObjectWriter("RLPn");
        lockedBalanceInBytes.write(this.amount);
        lockedBalanceInBytes.write(this.end);
        return lockedBalanceInBytes.toByteArray();
    }

    public LockedBalance newLockedBalance() {return new LockedBalance(this.amount, this.end);}

    public static LockedBalance toLockedBalance(byte[] lockedBalanceBytesArray) {
        if (lockedBalanceBytesArray == null) {
            return new LockedBalance();
        } else {
            ObjectReader lockedBalance = Context.newByteArrayObjectReader("RLPn", lockedBalanceBytesArray);
            return new LockedBalance(lockedBalance.readBigInteger(), lockedBalance.readBigInteger());
        }
    }
}
