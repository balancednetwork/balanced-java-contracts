# Incident recovery — governance execute batch

`governance-execute.json` is a Governance `execute(transactions)` batch (ArbitraryCallManager
format: `[{address, method, parameters:[{type,value}], value}]`) that contains the protocol
while the DEX same-token fix in this PR is deployed. It targets contracts owned by Balanced
Governance (`cx44250a…`), so the calls pass their `onlyOwner`/`onlyGovernance` checks. All 58
actions are admin/config setters and run fine while the protocol is under global shutdown.

## What it does (58 actions)

- **Block cross-chain transfers** — `setSpokeLimit(net, 0)` on **bnUSD** (12 chains) and **BALN**
  (10 chains). `HubTokenImpl.crossTransfer` has no `checkStatus`, so bridging works even during
  the shutdown; zeroing the spoke limits is what actually stops it. (`removeChain` only edits the
  `connectedChains` list, not `spokeContracts`/`spokeLimits`, so it does **not** block bridging.)
- **Loans lockdown** — `setRedemptionExemption(token, true)` ×18 disables redemption for every
  collateral, which neutralizes the `redeemCollateral` decimals bug **config-side (no Loans
  redeploy)**; `setDebtCeiling(symbol, 0)` ×18 blocks new borrows. `repay` / `withdraw` /
  `liquidate` remain available (verified decimals-safe in the deployed v1.3.0).

## Sequence

1. Execute this batch (works under the current global shutdown).
2. Deploy the DEX **v1.1.9** upgrade (this PR).
3. `enable()` on Governance to lift the shutdown — migration + repayment resume; redeem, borrow,
   and bridging stay blocked by the settings above.

## Not covered here — owned by OMM, not Balanced Governance

- **sICX cross-chain block** (`setSpokeLimit(net, 0)` on sICX) and the **Staking exchange-rate
  fix** (it divides by native `totalSupply` instead of `xTotalSupply`, so bridged sICX inflates
  the rate). sICX and the staking manager are owned by a separate account — these must be done by
  OMM.

## Notes

- **Redemption stays disabled** until a corrected Loans is shipped (fix `redeemCollateral` to use
  `10^tokenDecimals` instead of hardcoded `EXA`, matching `_liquidate`/`removeCollateral`). The
  exemptions are load-bearing — **do not clear them before that fix**, or the decimals bug
  re-arms (10^(18−d)× over-payout on sub-18-decimal collateral such as SUI/SOL/XLM).
