## Crosschain LP Feature Implementation Steps
The Crosschain Liquidity feature allows users to create a liquidity pool and/or provide
liquidity to a pool existing on the balanced from one of the spoke chain. Other activities
involved in this feature are staking the LP tokens, claiming rewards, unstaking the LP tokens,
 removing the liquidity from the pool and withdrawing the deposited amount. Adding the liquidity
 to a pool involves two steps 1) deposit a pair of tokens to the dex 2) add liquidity. Lets deep 
dive in each steps


### Create/Add liquidity
Creating a Liquidity pool requires two tokens (Base Token, Quote Token). The first step is to
deposit the tokens. To deposit the tokens from spoke chain we use Asset manager contract of the
spoke chain or Balanced Dollar Contract of the spoke chain of one of the token of the
pair is bnUSD.
#### Deposit tokens
 While depositing the token data field should have the following value:

```javascript
  function tokenData(method: string, params: Record<string, any>): Buffer {
     const map = {
       method: method,
       params: params,
     };
   
     const jsonString = JSON.stringify(map);
     return Buffer.from(jsonString, "utf-8");
  }
  
  let data = tokenData("_deposit", {});
```
#### Create a pool/Add liquidity
To create a pool the Quote Token needs to be added in the Dex priorly (Balanced governance only function). 
No other action to take, and simply adding liquidity will create a pool if there does not exist
already, otherwise it will simply add liquidity on the existing pool. Adding crosschain lp requires a call
to the spoke Xcall contract on `sendCall` method. A call to Solana Xcall contract looks like: 

```javascript
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let baseToken = "cx87f7f8ceaa054d46ba7343a2ecd21208e12913c6"; //testnet bnusd address
    let quoteToken = "cx6c46fbf0ac7e13c808424327bbf1cb4b699e14a5"; //testnet wrap sol address
    let data = getAddLPData(baseToken, quoteToken, baseAmount, quoteAmount, true, slippagePercentage); //get rlp encoded data
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_dex }; //icon nid hardcoded
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to) ....
```


### Stake LP Tokens
Once a user provide the liquidity to a pool, they get respective LP tokens as the proof of the liquidity.
They may want to stake the LP tokens if the pool is incentivised. A user can stake their LP tokens
with a crosschain call. The crosschain call will be a call to the spoke Xcall contract's `sendCall`
method that looks like: 
```javascript
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getStakeData("0x2.icon/cx0e04a92802d171a8d9f318f6568af47d68dba902", poolId, lpTokenAmount); //testnet stakedLp contract's network address hardcodec
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_dex };
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to) ....
```


### Claim rewards 
Once a user stakes their LP token of incentivised pool, the reward starts to be calculated, the User can
claim the rewards of minimum one day. To claim the rewards with crosschain call, here is a code sample for
Solana blockchain: 
```javascript
 let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getClaimRewardData("", []); //to filed is optional and protocol list is also optional
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_rewards };
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to) .....
```

### Unstake LP tokens
A user can unstake their LP tokens with a crosschain call on calling the `sendCall` method of spoke 
Xcall method. here is a sample script for the Solana blockchain
```javascript
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getUnStakeData(poolId, lpTokenAmount); //rlp encoded data
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_stakedlp };
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to)....
```
###
To remove the liquidity from a pool: 
```javascript
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let data = getXRemoveData(poolId, lpTokenAmout, true);
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_dex };
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to)....
```
###
To withdraw the deposited amount: 
```javascript
    let sources=xmState.sources;
    let destinations=xmState.destinations;
    let baseToken = "cx6c46fbf0ac7e13c808424327bbf1cb4b699e14a5"; //testnet wrapped sol token address
    let data = getWithdrawData(baseToken, withdrawAmount);
    let envelope = new Envelope(
        MessageType.CallMessage,
        new CallMessage(data).encode(),
        sources,
        destinations
      ).encode();
      const to = { "0": "0x2.icon/"+icon_dex };
      let sendCallIx = await xcall_program.methods
        .sendCall(Buffer.from(envelope), to)...
```