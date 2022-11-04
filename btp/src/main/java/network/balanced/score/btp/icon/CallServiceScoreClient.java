package network.balanced.score.btp.icon;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import java.lang.Deprecated;
import java.lang.Object;
import java.lang.RuntimeException;
import java.lang.String;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import score.annotation.Optional;

public final class CallServiceScoreClient extends DefaultScoreClient implements CallService {
  public CallServiceScoreClient(String url, BigInteger nid, Wallet wallet, Address address) {
    super(url, nid, wallet, address);
  }

  public CallServiceScoreClient(DefaultScoreClient client) {
    super(client);
  }

  public static CallServiceScoreClient _deploy(String url, BigInteger nid, Wallet wallet,
      String scoreFilePath, Map<String, Object> params) {
    return new CallServiceScoreClient(DefaultScoreClient._deploy(url,nid,wallet,scoreFilePath,params));
  }

  public static CallServiceScoreClient _of(Properties properties) {
    return _of("", properties);
  }

  public static CallServiceScoreClient _of(String prefix, Properties properties) {
    return new CallServiceScoreClient(DefaultScoreClient.of(prefix, properties));
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use sendCallMessage(Consumer<TransactionResult> consumerFunc, ...)
   * @throws java.lang.RuntimeException("not supported response of writable method in ScoreClient")
   */
  @Deprecated
  public BigInteger sendCallMessage(String _to, byte[] _data, @Optional byte[] _rollback) {
    throw new RuntimeException("not supported response of writable method in ScoreClient");
  }

  public void sendCallMessage(Consumer<TransactionResult> consumerFunc, String _to, byte[] _data,
      @Optional byte[] _rollback) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_data",_data);
    params.put("_rollback",_rollback);
    consumerFunc.accept(super._send("sendCallMessage", params));
  }

  public void sendCallMessage(BigInteger valueForPayable, String _to, byte[] _data,
      @Optional byte[] _rollback) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_data",_data);
    params.put("_rollback",_rollback);
    super._send(valueForPayable, "sendCallMessage", params);
  }

  public void sendCallMessage(Consumer<TransactionResult> consumerFunc, BigInteger valueForPayable,
      String _to, byte[] _data, @Optional byte[] _rollback) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_data",_data);
    params.put("_rollback",_rollback);
    consumerFunc.accept(super._send(valueForPayable, "sendCallMessage", params));
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use N/A
   * @throws java.lang.RuntimeException("not supported EventLog method")
   */
  @Deprecated
  public void RollbackMessage(BigInteger _sn, byte[] _rollback, String _reason) {
    throw new RuntimeException("not supported EventLog method");
  }

  public void RollbackMessage(Consumer<TransactionResult> consumerFunc, BigInteger _sn,
      byte[] _rollback, String _reason) {
    Map<String,Object> params = new HashMap<>();
    params.put("_sn",_sn);
    params.put("_rollback",_rollback);
    params.put("_reason",_reason);
    consumerFunc.accept(super._send("RollbackMessage", params));
  }

  public void executeRollback(BigInteger _sn) {
    Map<String,Object> params = new HashMap<>();
    params.put("_sn",_sn);
    super._send("executeRollback", params);
  }

  public void executeRollback(Consumer<TransactionResult> consumerFunc, BigInteger _sn) {
    Map<String,Object> params = new HashMap<>();
    params.put("_sn",_sn);
    consumerFunc.accept(super._send("executeRollback", params));
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use N/A
   * @throws java.lang.RuntimeException("not supported EventLog method")
   */
  @Deprecated
  public void CallMessage(String _from, String _to, BigInteger _sn, BigInteger _reqId,
      byte[] _data) {
    throw new RuntimeException("not supported EventLog method");
  }

  public void CallMessage(Consumer<TransactionResult> consumerFunc, String _from, String _to,
      BigInteger _sn, BigInteger _reqId, byte[] _data) {
    Map<String,Object> params = new HashMap<>();
    params.put("_from",_from);
    params.put("_to",_to);
    params.put("_sn",_sn);
    params.put("_reqId",_reqId);
    params.put("_data",_data);
    consumerFunc.accept(super._send("CallMessage", params));
  }

  public void executeCall(BigInteger _reqId) {
    Map<String,Object> params = new HashMap<>();
    params.put("_reqId",_reqId);
    super._send("executeCall", params);
  }

  public void executeCall(Consumer<TransactionResult> consumerFunc, BigInteger _reqId) {
    Map<String,Object> params = new HashMap<>();
    params.put("_reqId",_reqId);
    consumerFunc.accept(super._send("executeCall", params));
  }
}
