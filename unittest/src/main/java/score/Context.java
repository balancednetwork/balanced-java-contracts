/*
 * Copyright 2020 ICONLOOP Inc.
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

package score;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import score.impl.AnyDBImpl;
import score.impl.Crypto;
import score.impl.RLPObjectReader;
import score.impl.RLPObjectWriter;

import java.math.BigInteger;

public final class Context extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final StackWalker stackWalker =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private Context() {
    }

    public static byte[] getTransactionHash() {
        return null;
    }

    public static int getTransactionIndex() {
        return 0;
    }

    public static long getTransactionTimestamp() {
        return 0L;
    }

    public static BigInteger getTransactionNonce() {
        return BigInteger.ZERO;
    }

    public static Address getAddress() {
        return sm.getAddress();
    }

    public static Address getCaller() {
        return sm.getCaller();
    }

    public static Address getOrigin() {
        return sm.getOrigin();
    }

    public static Address getOwner() {
        return sm.getOwner();
    }

    public static BigInteger getValue() {
        return sm.getCurrentFrame().getValue();
    }

    public static long getBlockTimestamp() {
        return sm.getBlock().getTimestamp();
    }

    public static long getBlockHeight() {
        return sm.getBlock().getHeight();
    }

    public static BigInteger getBalance(Address address) throws IllegalArgumentException {
        return Account.getAccount(address).getBalance();
    }

    public static<T> T call(Class<T> cls, BigInteger value,
                            Address targetAddress, String method, Object... params) {
        return null;
    }

    public static Object call(BigInteger value,
                              Address targetAddress, String method, Object... params) {
        var caller = stackWalker.getCallerClass();
        return sm.call(caller, value, targetAddress, method, params);
    }

    public static<T> T call(Class<T> cls,
                            Address targetAddress, String method, Object... params) {
        var caller = stackWalker.getCallerClass();
        return cls.cast(sm.call(caller, BigInteger.ZERO, targetAddress, method, params));
    }

    public static Object call(Address targetAddress, String method, Object... params) {
        var caller = stackWalker.getCallerClass();
        return sm.call(caller, BigInteger.ZERO, targetAddress, method, params);
    }

    public static void transfer(Address targetAddress, BigInteger value) {
        var caller = stackWalker.getCallerClass();
        sm.call(caller, value, targetAddress, "fallback");
    }

    public static Address deploy(byte[] content, Object... params) {
        return null;
    }

    public static Address deploy(Address targetAddress, byte[] content, Object... params) {
        return null;
    }

    public static void revert(int code, String message) {
        throw new AssertionError(String.format("Reverted(%d): %s", code, message));
    }

    public static void revert(int code) {
        throw new AssertionError(String.format("Reverted(%d)", code));
    }

    public static void revert(String message) {
        revert(0, message);
    }

    public static void revert() {
        revert(0);
    }

    public static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void println(String message) {
        System.out.println(message);
    }

    public static byte[] hash(String alg, byte[] msg) {
        require(null != alg, "Algorithm can't be NULL");
        require(null != msg, "Message can't be NULL");
        return Crypto.hash(alg, msg);
    }

    public static boolean verifySignature(String alg, byte[] msg, byte[] sig, byte[] pubKey) {
        require(null != alg, "Algorithm can't be NULL");
        require(null != msg, "Message can't be NULL");
        require(null != sig, "Signature can't be NULL");
        require(null != pubKey, "Public key can't be NULL");
        return Crypto.verifySignature(alg, msg, sig, pubKey);
    }

    public static byte[] recoverKey(String alg, byte[] msg, byte[] sig, boolean compressed) {
        require(null != msg && null != sig);
        require(msg.length == 32, "the length of msg must be 32");
        require(sig.length == 65, "the length of sig must be 65");
        return Crypto.recoverKey(alg, msg, sig, compressed);
    }

    public static Address getAddressFromKey(byte[] pubKey) {
        require(null != pubKey, "pubKey can't be NULL");
        return new Address(Crypto.getAddressBytesFromKey(pubKey));
    }

    public static int getFeeSharingProportion() {
        return 0;
    }

    public static void setFeeSharingProportion(int proportion) {
    }

    @SuppressWarnings("unchecked")
    public static<K, V> BranchDB<K, V> newBranchDB(String id, Class<?> leafValueClass) {
        return new AnyDBImpl(id, leafValueClass);
    }

    @SuppressWarnings("unchecked")
    public static<K, V> DictDB<K, V> newDictDB(String id, Class<V> valueClass) {
        return new AnyDBImpl(id, valueClass);
    }

    @SuppressWarnings("unchecked")
    public static<E> ArrayDB<E> newArrayDB(String id, Class<E> valueClass) {
        return new AnyDBImpl(id, valueClass);
    }

    @SuppressWarnings("unchecked")
    public static<E> VarDB<E> newVarDB(String id, Class<E> valueClass) {
        return new AnyDBImpl(id, valueClass);
    }

    public static void logEvent(Object[] indexed, Object[] data) {
    }

    public static ObjectReader newByteArrayObjectReader(String codec, byte[] byteArray) {
        if ("RLPn".equals(codec)) {
            return new RLPObjectReader(byteArray);
        }
        throw new IllegalArgumentException("Unknown codec");
    }

    public static ByteArrayObjectWriter newByteArrayObjectWriter(String codec) {
        if ("RLPn".equals(codec)) {
            return new RLPObjectWriter();
        }
        throw new IllegalArgumentException("Unknown codec");
    }
}
