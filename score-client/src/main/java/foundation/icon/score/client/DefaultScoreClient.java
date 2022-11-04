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

package foundation.icon.score.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.crypto.KeystoreException;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.IconJsonModule;
import foundation.icon.jsonrpc.JsonrpcClient;
import foundation.icon.jsonrpc.SendTransactionParamSerializer;
import foundation.icon.jsonrpc.model.*;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Base64;
import score.UserRevertedException;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class DefaultScoreClient extends JsonrpcClient {
    public static final Address ZERO_ADDRESS = new Address("cx0000000000000000000000000000000000000000");
    public static final BigInteger DEFAULT_STEP_LIMIT = new BigInteger("9502f900", 16);
    public static final long BLOCK_INTERVAL = 1;
    public static final long DEFAULT_RESULT_RETRY_WAIT = 0;
    public static final long DEFAULT_RESULT_TIMEOUT = 10000;

    protected final BigInteger nid;
    protected final Wallet wallet;
    protected final Address address;
    protected BigInteger stepLimit = DEFAULT_STEP_LIMIT;
    protected long resultTimeout = DEFAULT_RESULT_TIMEOUT;

    public DefaultScoreClient(String url, String nid, String keyStorePath, String keyStorePassword, String address) {
        this(url, nid(nid), wallet(keyStorePath, keyStorePassword), new Address(address));
    }

    public DefaultScoreClient(String url, BigInteger nid, Wallet wallet, Address address) {
        super(url);
        initialize(this);

        this.nid = nid;
        this.wallet = wallet;
        this.address = address;
    }

    public DefaultScoreClient(DefaultScoreClient client) {
        super(client.endpoint);
        initialize(this);

        this.nid = client._nid();
        this.wallet = client._wallet();
        this.address = client._address();
        this.stepLimit = client._stepLimit();
        this.resultTimeout = client._resultTimeout();
    }

    static void initialize(JsonrpcClient client) {
        client.mapper().registerModule(new IconJsonModule());
        client.mapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static DefaultScoreClient _deploy(String url, String nid, String keyStorePath, String keyStorePassword, String scoreFilePath, Map<String, Object> params) {
        return _deploy(url, nid(nid), wallet(keyStorePath, keyStorePassword), scoreFilePath, params);
    }

    public static DefaultScoreClient _deploy(String url, BigInteger nid, Wallet wallet, String scoreFilePath, Map<String, Object> params) {
        JsonrpcClient client = new JsonrpcClient(url);
        initialize(client);
        Address address = deploy(client, nid, wallet, DEFAULT_STEP_LIMIT, ZERO_ADDRESS, scoreFilePath, params, DEFAULT_RESULT_TIMEOUT);
        return new DefaultScoreClient(url, nid, wallet, address);
    }

    public static DefaultScoreClient _deploy(String url, BigInteger nid, Wallet wallet, byte[] content, Map<String, Object> params) {
        JsonrpcClient client = new JsonrpcClient(url);
        initialize(client);
        Address address = deploy(client, nid, wallet, DEFAULT_STEP_LIMIT, ZERO_ADDRESS, content, params, DEFAULT_RESULT_TIMEOUT);
        return new DefaultScoreClient(url, nid, wallet, address);
    }

    public static Hash _deployAsync(String url, BigInteger nid, Wallet wallet, String scoreFilePath, Map<String, Object> params) {
        JsonrpcClient client = new JsonrpcClient(url);
        initialize(client);
        return deployAsync(client, nid, wallet, DEFAULT_STEP_LIMIT, ZERO_ADDRESS, scoreFilePath, params);
    }

    public static DefaultScoreClient getDeploymentResult(String url, BigInteger nid, Wallet wallet, Hash hash)  {
        JsonrpcClient client = new JsonrpcClient(url);
        initialize(client);
        TransactionResult txr = result(client, hash, DEFAULT_RESULT_TIMEOUT);
        Address address = txr.getScoreAddress();
        return new DefaultScoreClient(url, nid, wallet, address);
    }

    public void _update(String scoreFilePath, Map<String, Object> params) {
        deploy(this, nid, wallet, DEFAULT_STEP_LIMIT, address, scoreFilePath, params, DEFAULT_RESULT_TIMEOUT);
    }

    public BigInteger _nid() {
        return nid;
    }

    public Wallet _wallet() {
        return wallet;
    }

    public Address _address() {
        return address;
    }

    public BigInteger _stepLimit() {
        return stepLimit;
    }

    public void _stepLimit(BigInteger stepLimit) {
        this.stepLimit = stepLimit;
    }

    public long _resultTimeout() {
        return resultTimeout;
    }

    public void _resultTimeout(long resultTimeout) {
        this.resultTimeout = resultTimeout;
    }

    public <T> T _call(Class<T> responseType, String method, Map<String, Object> params) {
        return call(this, responseType, address, method, params);
    }

    public <T> T _call(TypeReference<T> responseType, String method, Map<String, Object> params) {
        return call(this, responseType, address, method, params);
    }

    public TransactionResult _send(String method, Map<String, Object> params) {
        return send(this, nid, wallet, stepLimit, address, null, method, params, resultTimeout);
    }

    public TransactionResult _send(BigInteger valueForPayable, String method, Map<String, Object> params) {
        return send(this, nid, wallet, stepLimit, address, valueForPayable, method, params, resultTimeout);
    }

    public TransactionResult _transfer(Address to, BigInteger value, String message) {
        return transfer(this, nid, wallet, stepLimit, to, value, message, resultTimeout);
    }

    public BigInteger _balance() {
        return balance(this, address);
    }

    public BigInteger _balance(Address address) {
        return balance(this, address);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BlockHeight {
        BigInteger height;

        public BigInteger getHeight() {
            return height;
        }
    }

    public BigInteger _lastBlockHeight() {
        return lastBlock(this, BlockHeight.class).height;
    }


    public static DefaultScoreClient of(Properties properties) {
        return of("", properties);
    }

    public static DefaultScoreClient of(Properties properties, Map<String, Object> params) {
        return of("", properties, params);
    }

    public static DefaultScoreClient of(String prefix, Properties properties) {
        return of(prefix, properties, null);
    }

    public static DefaultScoreClient of(String prefix, Properties properties, Map<String, Object> params) {
        String url = url(prefix, properties);
        BigInteger nid = nid(prefix, properties);
        Wallet wallet = wallet(prefix, properties);
        Address address = address(prefix, properties);
        String scoreFilePath = scoreFilePath(prefix, properties);
        Map<String, Object> deployParams = params(prefix, properties, params);
        if (address == null) {
            System.out.printf("deploy prefix: %s, url: %s, nid: %s, keyStorePath: %s, scoreFilePath: %s, params: %s%n",
                    prefix, url, nid, wallet != null ? wallet.getAddress() : wallet, scoreFilePath, deployParams);
            return _deploy(url, nid, wallet, scoreFilePath, deployParams);
        } else {
            System.out.printf("prefix: %s, url: %s, nid: %s, wallet: %s, address: %s%n",
                    prefix, url, nid, wallet != null ? wallet.getAddress() : wallet, address);
            DefaultScoreClient client = new DefaultScoreClient(url, nid, wallet, address);
            if (isUpdate(prefix, properties) && scoreFilePath != null && !scoreFilePath.isEmpty()) {
                System.out.printf("update scoreFilePath: %s, params: %s%n", scoreFilePath, deployParams);
                client._update(scoreFilePath, deployParams);
            }
            return client;
        }
    }

    public static String url(Properties properties) {
        return url("", properties);
    }

    public static String url(String prefix, Properties properties) {
        return properties.getProperty(prefix+"url");
    }

    public static BigInteger nid(Properties properties) {
        return nid("", properties);
    }

    public static BigInteger nid(String prefix, Properties properties) {
        return nid(properties.getProperty(prefix+"nid"));
    }

    public static BigInteger nid(String nid) {
        if (nid.startsWith("0x")) {
            return new BigInteger(nid.substring(2), 16);
        } else {
            return new BigInteger(nid);
        }
    }

    public static Wallet wallet(Properties properties) {
        return wallet("", properties);
    }

    public static Wallet wallet(String prefix, Properties properties) {
        String keyStore = properties.getProperty(prefix+"keyStore");
        if (keyStore == null || keyStore.isEmpty()) {
            return null;
        }
        String keyPassword = properties.getProperty(prefix+"keyPassword");
        if (keyPassword == null || keyPassword.isEmpty()) {
            String keySecret = properties.getProperty(prefix+"keySecret");
            try {
                System.out.println("using keySecret "+keySecret);
                keyPassword = Files.readString(Path.of(keySecret));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return wallet(keyStore, keyPassword);
    }

    public static Wallet wallet(String keyStorePath, String keyStorePassword) {
        try {
            System.out.println("load wallet "+keyStorePath);
            return KeyWallet.load(keyStorePassword, new File(keyStorePath));
        } catch (IOException | KeystoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static Address address(Properties properties) {
        return address("", properties);
    }

    public static Address address(String prefix, Properties properties) {
        String address = properties.getProperty(prefix+"address");
        if (address == null || address.isEmpty()) {
            return null;
        }
        return address(address);
    }

    public static Address address(String address) {
        return new Address(address);
    }

    public static boolean isUpdate(Properties properties) {
        return isUpdate("", properties);
    }

    public static boolean isUpdate(String prefix, Properties properties) {
        return Boolean.parseBoolean(
                (String)properties.getOrDefault(prefix+"isUpdate",
                        Boolean.FALSE.toString()));
    }

    public static String scoreFilePath(Properties properties) {
        return scoreFilePath("", properties);
    }

    public static String scoreFilePath(String prefix, Properties properties) {
        return properties.getProperty(prefix+"scoreFilePath");
    }

    public static Map<String, Object> params(Properties properties) {
        return params("", properties);
    }

    public static Map<String, Object> params(String prefix, Properties properties) {
        return params(prefix, properties, null);
    }

    public static Map<String, Object> params(String prefix, Properties properties, Map<String, Object> overwrite) {
        String paramsKey = prefix+"params.";
        Map<String, Object> params = new HashMap<>();
        for(Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = ((String)entry.getKey());
            if (key.startsWith(paramsKey)) {
                params.put(key.substring(paramsKey.length()), entry.getValue());
            }
        }
        if (overwrite != null) {
            for(Map.Entry<String, Object> entry : overwrite.entrySet()) {
                params.put(entry.getKey(), entry.getValue());
            }
        }
        return params.isEmpty() ? null : params;
    }


    public static CallData callData(String method, Map<String, Object> params) {
        return new CallData(method, params != null && !params.isEmpty() ? params : null);
    }

    public static <T> T call(
            JsonrpcClient client, Class<T> responseType, Address address,
            String method, Map<String, Object> params) {
        return client.request(responseType, "icx_call", new CallParam(address, callData(method, params)));
    }

    public static <T> T call(
            JsonrpcClient client, TypeReference<T> responseType, Address address,
            String method, Map<String, Object> params) {
        return client.request(responseType, "icx_call", new CallParam(address, callData(method, params)));
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
        // System.out.printf("wait block interval %d msec%n", BLOCK_INTERVAL);
        // try {
        //     Thread.sleep(BLOCK_INTERVAL);
        // } catch (InterruptedException ie) {
        //     ie.printStackTrace();
        // }
    }

    public static TransactionResult send(
            JsonrpcClient client, BigInteger nid, Wallet wallet, BigInteger stepLimit, Address address,
            BigInteger valueForPayable, String method, Map<String, Object> params,
            long timeout) {
        SendTransactionParam tx = new SendTransactionParam(nid, address, valueForPayable, "call", callData(method, params));
        Hash txh = sendTransaction(client, wallet, tx);
        waitBlockInterval();
        return result(client, txh, timeout);
    }

    public static Address deploy(
            JsonrpcClient client, BigInteger nid, Wallet wallet, BigInteger stepLimit, Address address,
            String scoreFilePath, Map<String, Object> params,
            long timeout) {

        byte[] content;
        try {
            content = Files.readAllBytes(Path.of(scoreFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String contentType;
        if (scoreFilePath.endsWith(".jar")) {
            contentType = "application/java";
        } else if (scoreFilePath.endsWith(".zip")) {
            contentType = "application/zip";
        } else {
            throw new RuntimeException("not supported score file");
        }
        SendTransactionParam tx = new SendTransactionParam(nid, address,null,"deploy", new DeployData(contentType, content, params));
        Hash txh = sendTransaction(client, wallet, tx);
        waitBlockInterval();
        TransactionResult txr = result(client, txh, timeout);
        System.out.println("SCORE address: "+txr.getScoreAddress());
        return txr.getScoreAddress();
    }

    public static Address deploy(
        JsonrpcClient client, BigInteger nid, Wallet wallet, BigInteger stepLimit, Address address,
        byte[] content, Map<String, Object> params,
        long timeout) {

    String contentType = "application/java";
    SendTransactionParam tx = new SendTransactionParam(nid, address,null,"deploy", new DeployData(contentType, content, params));
    Hash txh = sendTransaction(client, wallet, tx);
    waitBlockInterval();
    TransactionResult txr = result(client, txh, timeout);
    System.out.println("SCORE address: "+txr.getScoreAddress());
    return txr.getScoreAddress();
}

    public static Hash deployAsync(
            JsonrpcClient client, BigInteger nid, Wallet wallet, BigInteger stepLimit, Address address,
            String scoreFilePath, Map<String, Object> params) {
        byte[] content;
        try {
            content = Files.readAllBytes(Path.of(scoreFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String contentType;
        if (scoreFilePath.endsWith(".jar")) {
            contentType = "application/java";
        } else if (scoreFilePath.endsWith(".zip")) {
            contentType = "application/zip";
        } else {
            throw new RuntimeException("not supported score file");
        }

        SendTransactionParam tx = new SendTransactionParam(nid, address,null,"deploy", new DeployData(contentType, content, params));
        return sendTransaction(client, wallet, tx);
    }

    public static TransactionResult transfer(
            JsonrpcClient client, BigInteger nid, Wallet wallet, BigInteger stepLimit, Address address,
            BigInteger value, String message, long timeout) {
        SendTransactionParam tx;
        if (message != null) {
            tx = new SendTransactionParam(nid, address, value, "message", message.getBytes(StandardCharsets.UTF_8));
        } else {
            tx = new SendTransactionParam(nid, address, value, null, null);
        }
        Hash txh = sendTransaction(client, wallet, tx);
        waitBlockInterval();
        return result(client, txh, timeout);
    }

    public static TransactionResult result(JsonrpcClient client, Hash txh, long timeout) {
        Map<String, Object> params = Map.of("txHash", txh);
        long etime = System.currentTimeMillis() + timeout;
        TransactionResult txr = null;
        while(txr == null) {
            try {
                txr = client.request(TransactionResult.class, "icx_getTransactionResult", params);
            } catch (JsonrpcClient.JsonrpcError e) {
                if (e.getCode() == -31002 /* pending */
                        || e.getCode() == -31003 /* executing */
                        || e.getCode() == -31004 /* not found */) {
                    if (timeout > 0 && System.currentTimeMillis() >= etime) {
                        throw new RuntimeException("timeout");
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

    public static BigInteger balance(JsonrpcClient client, Address address) {
        return client.request(BigInteger.class, "icx_getBalance", Map.of("address", address));
    }

    public static <T> T lastBlock(JsonrpcClient client, Class<T> blockType) {
        return client.request(blockType, "icx_getLastBlock", null);
    }

}
