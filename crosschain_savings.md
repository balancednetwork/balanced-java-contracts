## Crosschain Savings Feature Implementation Steps
The Crosschain Savings feature allows users to deposit(lock) their bnUSD balance on any spoke 
chain into savings contract on ICON blockchain to receive the savings rewards as per savings rate. 

#### Deposit bnUSD
  Depositing bnUSD on spoke chain is the normal process of bnUSD crossTransfer from spoke chain. But 
  the data field shouldn't be empty in this case. Data should contain a rlp encoded value as specified on
  below code for solana spoke chain:
```javascript
    let data = tokenData("_lock", {"to":"solana-test/"+withdrawerKeyPair.publicKey});
    function tokenData(method: string, params: Record<string, any>): Buffer {
      const map = {
        method: method,
        params: params,
      };
    
      const jsonString = JSON.stringify(map);
    
      return Buffer.from(jsonString, "utf-8");
    }
```
 Another way of bnUSD deposit from the spoke chain using other supported tokens is deposit token to
 Asset Manager contract of the spoke chain. Doing that the `to` field should be the 'ICON Router 
 Contract Address' and data should be prepared on the following way. 
```javascript
    function getLockData(method: string, params: Record<string, any>): Buffer {
      const map = {
        method: method,
        params: params,
      };
      const jsonString = JSON.stringify(map);
      return Buffer.from(jsonString, "utf-8");
    }
    
    function getData(
    ): Uint8Array {
      let lockData = getLockData("_lock", {"to": to_netwrok_address});
      const receiver = Buffer.from(icon_savings_network_address, "utf8");
      let rlpInput: rlp.Input = [
        "_swap",
        receiver,
        "0x00", // minimum amount to receive
        lockData,
        ["0x01", icon_bn_usd]
      ];
      return rlp.encode(rlpInput);
    }
```
### Claim rewards
To claim the accumulated rewards as per the savings rate, following is the example used from the solana
spooke chain:
```javascript
    function getClaimRewardData(
    ): Uint8Array {
      let rlpInput: rlp.Input = [
        "xclaimrewards",
      ];
      return rlp.encode(rlpInput);
    }
    
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getClaimRewardData();
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
    ).encode();
    const to = { "0": "0x2.icon/"+icon_savings };
    let sendCallIx = await xcall_program.methods
      .sendCall(Buffer.from(envelope), to)...
```
### Unlock
To unlock the locked bnUSD:
```javascript
    function getUnlockData(
        amount: number,
    ): Uint8Array {
        let rlpInput: rlp.Input = [
          "xunlock",
          amount
        ];
        return rlp.encode(rlpInput);
    }
      
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getUnlockData(amount); // amount to unlock
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
    ).encode();
    const to = { "0": "0x2.icon/"+icon_savings };
    console.log(Buffer.from(envelope));
    let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to)...

```
## USDC Staking feature
USDC staking is a feature in which USDC can be locked to the savings contract to earn the savings
rate rewards. When staking USDC the Route contract is used to convert USDC to bnUSD and then it is transferred
to the savings contract. When unlocking it user will get bnUSD as normal bnUSD unlocking. By crosschain
savings features enabled even spoke chain USDC can be staked now(example is provided above- any supported 
token can be staked now from the spoke chain). In ICON chain it can be done by transfering USDC to `Router`
Contract with following data build with java code for example: 
```java
    public static byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }
            
    String tokenData = new String(tokenData("_lock", Map.of()));
    Account newReceiver = balanced.savings.account;
    byte[] data = tokenData("_swap", Map.of("path",
    new Object[]{balanced.bnUSD.getAddress().toString()}, "receiver",
    newReceiver.getAddress().toString(), "data", tokenData));
```