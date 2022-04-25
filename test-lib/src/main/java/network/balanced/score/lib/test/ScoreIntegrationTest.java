package network.balanced.score.lib.test;

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


import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.jsonrpc.Address;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.RevertedException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;
import score.UserRevertedException;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
public interface ScoreIntegrationTest {

    static <T> int indexOf(T[] array, T value) {
        return indexOf(array, value::equals);
    }

    static <T> int indexOf(T[] array, Predicate<T> predicate) {
        for (int i = 0; i < array.length; i++) {
            if (predicate.test(array[i])) {
                return i;
            }
        }
        return -1;
    }

    static boolean contains(Map<String, Object> map, String key, Object value) {
        return contains(map, key, value::equals);
    }

    static <T> boolean contains(Map<String, T> map, String key, Predicate<T> predicate) {
        return map.containsKey(key) && predicate.test(map.get(key));
    }

    static <T> List<T> eventLogs(TransactionResult txr,
                                 String signature,
                                 Address scoreAddress,
                                 Function<TransactionResult.EventLog, T> mapperFunc,
                                 Predicate<T> filter) {
        Predicate<TransactionResult.EventLog> predicate =
                (el) -> el.getIndexed().get(0).equals(signature);
        if (scoreAddress != null) {
            predicate = predicate.and((el) -> el.getScoreAddress().equals(scoreAddress.toString()));
        }
        Stream<T> stream = txr.getEventLogs().stream()
                .filter(predicate)
                .map(mapperFunc);
        if (filter != null) {
            stream = stream.filter(filter);
        }
        return stream.collect(Collectors.toList());
    }

    DefaultScoreClient client = new DefaultScoreClient(
            DefaultScoreClient.url(System.getProperties()),
            DefaultScoreClient.nid(System.getProperties()),
            null,
            null
    );

    static void waitByNumOfBlock(long numOfBlock) {
        waitByHeight(client._lastBlockHeight().add(BigInteger.valueOf(numOfBlock)));
    }

    static void waitByHeight(long waitHeight) {
        waitByHeight(BigInteger.valueOf(waitHeight));
    }

    static void waitByHeight(BigInteger waitHeight) {
        BigInteger height = client._lastBlockHeight();
        while (height.compareTo(waitHeight) < 0) {
            System.out.println("height: " + height + ", waitHeight: " + waitHeight);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            height = client._lastBlockHeight();
        }
    }

    static void balanceCheck(Address address, BigInteger value, Executable executable) {
        BigInteger balance = client._balance(address);
        try {
            executable.execute();
        } catch (UserRevertedException | RevertedException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        assertEquals(balance.add(value), client._balance(address));
    }

    @FunctionalInterface
    interface EventLogsSupplier<T> {
        List<T> apply(TransactionResult txr, Address address, Predicate<T> filter);
    }

    static <T> Consumer<TransactionResult> eventLogChecker(
            Address address, EventLogsSupplier<T> supplier, Consumer<T> consumer) {
        return (txr) -> {
            List<T> eventLogs = supplier.apply(txr, address, null);
            assertEquals(1, eventLogs.size());
            if (consumer != null) {
                consumer.accept(eventLogs.get(0));
            }
        };
    }

    static <T> Consumer<TransactionResult> eventLogsChecker(
            Address address, EventLogsSupplier<T> supplier, Consumer<List<T>> consumer) {
        return (txr) -> {
            List<T> eventLogs = supplier.apply(txr, address, null);
            if (consumer != null) {
                consumer.accept(eventLogs);
            }
        };
    }


    static Wallet getOrGenerateWallet(String prefix, Properties properties) {
        Wallet wallet = DefaultScoreClient.wallet(prefix, properties);
        return wallet == null ? generateWallet() : wallet;
    }

    static Wallet getOrGenerateWallet(Properties properties) {
        if (properties == null) {
            return generateWallet();
        }
        Wallet wallet = DefaultScoreClient.wallet(properties);
        return wallet == null ? generateWallet() : wallet;
    }

    static KeyWallet generateWallet() {
        try {
            return KeyWallet.create();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

}