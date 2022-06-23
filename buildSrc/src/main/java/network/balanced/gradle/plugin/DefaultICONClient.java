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

package network.balanced.gradle.plugin;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.IconJsonModule;
import foundation.icon.jsonrpc.JsonrpcClient;
import foundation.icon.jsonrpc.SendTransactionParamSerializer;
import foundation.icon.jsonrpc.model.*;
import foundation.icon.score.client.RevertedException;
import network.balanced.gradle.plugin.utils.Network;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Base64;
import score.UserRevertedException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static foundation.icon.score.client.DefaultScoreClient.call;
import static foundation.icon.score.client.DefaultScoreClient.callData;

public class DefaultICONClient {

    public static final Address ZERO_ADDRESS = new Address("cx0000000000000000000000000000000000000000");
    private static final BigInteger DEFAULT_STEP_LIMIT = new BigInteger("9502f900", 16);
    private static final long BLOCK_INTERVAL = 1000;
    private static final long DEFAULT_RESULT_RETRY_WAIT = 1000;
    static final long DEFAULT_RESULT_TIMEOUT = 10000;

    private final JsonrpcClient client;
    private final BigInteger nid;

    public DefaultICONClient(Network network) {
        client = new JsonrpcClient(network.getUrl());
        client.mapper().registerModule(new IconJsonModule());
        client.mapper().setSerializationInclusion(Include.NON_NULL);

        this.nid = network.getNid();

    }

    public Address deploy(Wallet wallet, Address address, String scoreFilePath, Map<String, Object> params,
                          String contentType) {
        return this.deploy(wallet, address, scoreFilePath, params, DEFAULT_RESULT_TIMEOUT, contentType);
    }

    public Address deploy(Wallet wallet, Address address, String scorePath, Map<String, Object> params, long timeout,
                          String contentType) {

        if (contentType == null) {
            if (scorePath.endsWith(".jar")) {
                contentType = "application/java";
            } else if (scorePath.endsWith(".zip")) {
                contentType = "application/zip";
            } else {
                throw new RuntimeException("not supported score file");
            }
        }

        byte[] content;
        int count = 0;
        int maxTries = 3;
        while (true) {
            try {
                URI uri = getURI(scorePath);
                content = IOUtils.toByteArray(uri);
                break;
            } catch (IOException e) {
                if (++count >= maxTries) throw new RuntimeException(e);
            }
        }
        return deploy(wallet, address, params, timeout, content, contentType);
    }

    private Address deploy(Wallet wallet, Address address, Map<String, Object> params,
                           long timeout, byte[] content, String contentType) {
        SendTransactionParam tx = new SendTransactionParam(nid, address, null, "deploy", new DeployData(contentType,
                content, params));
        Hash txh = sendTransaction(client, wallet, tx);
        System.out.println("txh = " + txh);
        waitBlockInterval();
        TransactionResult txr = result(client, txh, timeout);
        System.out.println("SCORE address: " + txr.getScoreAddress());
        return txr.getScoreAddress();
    }

    public TransactionResult send(Wallet wallet, Address address,
                                  BigInteger valueForPayable, String method, Map<String, Object> params,
                                  long timeout) {
        SendTransactionParam tx = new SendTransactionParam(nid, address, valueForPayable, "call",
                callData(method, params));
        Hash txh = sendTransaction(client, wallet, tx);
        waitBlockInterval();
        return result(client, txh, timeout);
    }

    public <T> T _call(TypeReference<T> responseType, Address address, String method, Map<String, Object> params) {
        return call(client, responseType, address, method, params);
    }

    public static URI getURI(String url) {
        try {
            URL obj = new URL(url);
            return obj.toURI();
        } catch (MalformedURLException | URISyntaxException ignored) {
            return Path.of(url).toUri();
        }
    }

    public static TransactionResult result(JsonrpcClient client, Hash txh, long timeout) {
        Map<String, Object> params = Map.of("txHash", txh);
        long etime = System.currentTimeMillis() + timeout;
        TransactionResult txr = null;
        while (txr == null) {
            try {
                txr = client.request(TransactionResult.class, "icx_getTransactionResult", params);
            } catch (JsonrpcClient.JsonrpcError e) {
                if (e.getCode() == -31002 /* pending */
                        || e.getCode() == -31003 /* executing */
                        || e.getCode() == -31004 /* not found */) {
                    if (timeout > 0 && System.currentTimeMillis() >= etime) {
                        throw new RuntimeException("timeout");
                    }
                    try {
                        Thread.sleep(DEFAULT_RESULT_RETRY_WAIT);
                        System.out.println("wait for " + txh);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
        if (!BigInteger.ONE.equals(txr.getStatus())) {
            TransactionResult.Failure failure = txr.getFailure();
            int revertCode = failure.getCode().intValue();
            String revertMessage = failure.getMessage();
            if (revertCode >= 32) {
                throw new UserRevertedException(revertCode - 32, revertMessage);
            } else {
                throw new RevertedException(revertCode, revertMessage);
            }
        }
        return txr;
    }


    static Hash sendTransaction(JsonrpcClient client, Wallet wallet, SendTransactionParam sendTransactionParam) {
        Objects.requireNonNull(client, "client required not null");
        Objects.requireNonNull(wallet, "wallet required not null");
        Objects.requireNonNull(wallet, "sendTransactionParam required not null");

        sendTransactionParam.setFrom(Address.of(wallet));
        if (sendTransactionParam.getTimestamp() == null) {
            sendTransactionParam.setTimestamp(TransactionParam.currentTimestamp());
        }
        if (sendTransactionParam.getStepLimit() == null) {
            sendTransactionParam.setStepLimit(DEFAULT_STEP_LIMIT);
        }
        if (sendTransactionParam.getNid() == null) {
            throw new IllegalArgumentException("nid could not be null");
        }

        Map<String, Object> params = new HashMap<>();
        String serialized;
        try {
            serialized = SendTransactionParamSerializer.serialize(sendTransactionParam, params);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] digest = new SHA3.Digest256().digest(serialized.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.toBase64String(wallet.sign(digest));
        params.put("signature", signature);
        return client.request(Hash.class, "icx_sendTransaction", params);
    }

    static void waitBlockInterval() {
        System.out.printf("wait block interval %d msec%n", BLOCK_INTERVAL);
        try {
            Thread.sleep(BLOCK_INTERVAL);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }
}
