package network.balanced.score.btp.icon;

import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import foundation.icon.score.client.DefaultScoreClient;
import java.lang.Boolean;
import java.lang.Deprecated;
import java.lang.Object;
import java.lang.RuntimeException;
import java.lang.String;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public final class MockBMCScoreClient extends DefaultScoreClient implements MockBMC {
  public MockBMCScoreClient(String url, BigInteger nid, Wallet wallet, Address address) {
    super(url, nid, wallet, address);
  }

  public MockBMCScoreClient(DefaultScoreClient client) {
    super(client);
  }

  public static MockBMCScoreClient _deploy(String url, BigInteger nid, Wallet wallet,
      String scoreFilePath, Map<String, Object> params) {
    return new MockBMCScoreClient(DefaultScoreClient._deploy(url,nid,wallet,scoreFilePath,params));
  }

  public static MockBMCScoreClient _of(Properties properties) {
    return _of("", properties);
  }

  public static MockBMCScoreClient _of(String prefix, Properties properties) {
    return new MockBMCScoreClient(DefaultScoreClient.of(prefix, properties));
  }

  public void setNet(String _net) {
    Map<String,Object> params = new HashMap<>();
    params.put("_net",_net);
    super._send("setNet", params);
  }

  public void setNet(Consumer<TransactionResult> consumerFunc, String _net) {
    Map<String,Object> params = new HashMap<>();
    params.put("_net",_net);
    consumerFunc.accept(super._send("setNet", params));
  }

  public String getNet() {
    return super._call(String.class, "getNet", null);
  }

  public void handleRelayMessage(score.Address _addr, String _prev, BigInteger _seq, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_prev",_prev);
    params.put("_seq",_seq);
    params.put("_msg",_msg);
    super._send("handleRelayMessage", params);
  }

  public void handleRelayMessage(Consumer<TransactionResult> consumerFunc, score.Address _addr,
      String _prev, BigInteger _seq, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_prev",_prev);
    params.put("_seq",_seq);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send("handleRelayMessage", params));
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use N/A
   * @throws java.lang.RuntimeException("not supported EventLog method")
   */
  @Deprecated
  public void HandleRelayMessage(byte[] _ret) {
    throw new RuntimeException("not supported EventLog method");
  }

  public void HandleRelayMessage(Consumer<TransactionResult> consumerFunc, byte[] _ret) {
    Map<String,Object> params = new HashMap<>();
    params.put("_ret",_ret);
    consumerFunc.accept(super._send("HandleRelayMessage", params));
  }

  public String getBtpAddress() {
    return super._call(String.class, "getBtpAddress", null);
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use sendMessage(Consumer<TransactionResult> consumerFunc, ...)
   * @throws java.lang.RuntimeException("not supported response of writable method in ScoreClient")
   */
  @Deprecated
  public BigInteger sendMessage(String _to, String _svc, BigInteger _sn, byte[] _msg) {
    throw new RuntimeException("not supported response of writable method in ScoreClient");
  }

  public void sendMessage(Consumer<TransactionResult> consumerFunc, String _to, String _svc,
      BigInteger _sn, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send("sendMessage", params));
  }

  public void sendMessage(BigInteger valueForPayable, String _to, String _svc, BigInteger _sn,
      byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    super._send(valueForPayable, "sendMessage", params);
  }

  public void sendMessage(Consumer<TransactionResult> consumerFunc, BigInteger valueForPayable,
      String _to, String _svc, BigInteger _sn, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send(valueForPayable, "sendMessage", params));
  }

  /**
   * @deprecated Do not use this method, this is generated only for preventing compile error.
   *  Instead, use N/A
   * @throws java.lang.RuntimeException("not supported EventLog method")
   */
  @Deprecated
  public void SendMessage(BigInteger _nsn, String _to, String _svc, BigInteger _sn, byte[] _msg) {
    throw new RuntimeException("not supported EventLog method");
  }

  public void SendMessage(Consumer<TransactionResult> consumerFunc, BigInteger _nsn, String _to,
      String _svc, BigInteger _sn, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_nsn",_nsn);
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send("SendMessage", params));
  }

  public BigInteger getNsn() {
    return super._call(BigInteger.class, "getNsn", null);
  }

  public void addResponse(String _to, String _svc, BigInteger _sn) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    super._send("addResponse", params);
  }

  public void addResponse(Consumer<TransactionResult> consumerFunc, String _to, String _svc,
      BigInteger _sn) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    consumerFunc.accept(super._send("addResponse", params));
  }

  public boolean hasResponse(String _to, String _svc, BigInteger _sn) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    return super._call(Boolean.class, "hasResponse", params);
  }

  public void clearResponse() {
    super._send("clearResponse", null);
  }

  public void clearResponse(Consumer<TransactionResult> consumerFunc) {
    consumerFunc.accept(super._send("clearResponse", null));
  }

  public void setFee(BigInteger _forward, BigInteger _backward) {
    Map<String,Object> params = new HashMap<>();
    params.put("_forward",_forward);
    params.put("_backward",_backward);
    super._send("setFee", params);
  }

  public void setFee(Consumer<TransactionResult> consumerFunc, BigInteger _forward,
      BigInteger _backward) {
    Map<String,Object> params = new HashMap<>();
    params.put("_forward",_forward);
    params.put("_backward",_backward);
    consumerFunc.accept(super._send("setFee", params));
  }

  public BigInteger getFee(String _to, boolean _response) {
    Map<String,Object> params = new HashMap<>();
    params.put("_to",_to);
    params.put("_response",_response);
    return super._call(BigInteger.class, "getFee", params);
  }

  public void handleBTPMessage(score.Address _addr, String _from, String _svc, BigInteger _sn,
      byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_from",_from);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    super._send("handleBTPMessage", params);
  }

  public void handleBTPMessage(Consumer<TransactionResult> consumerFunc, score.Address _addr,
      String _from, String _svc, BigInteger _sn, byte[] _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_from",_from);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send("handleBTPMessage", params));
  }

  public void handleBTPError(score.Address _addr, String _src, String _svc, BigInteger _sn,
      long _code, String _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_src",_src);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_code",_code);
    params.put("_msg",_msg);
    super._send("handleBTPError", params);
  }

  public void handleBTPError(Consumer<TransactionResult> consumerFunc, score.Address _addr,
      String _src, String _svc, BigInteger _sn, long _code, String _msg) {
    Map<String,Object> params = new HashMap<>();
    params.put("_addr",_addr);
    params.put("_src",_src);
    params.put("_svc",_svc);
    params.put("_sn",_sn);
    params.put("_code",_code);
    params.put("_msg",_msg);
    consumerFunc.accept(super._send("handleBTPError", params));
  }
}
