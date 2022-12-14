# Table of Contents

- [Table of Contents](#table-of-contents)
- [Boosted Baln](#boosted-baln)
- [Rewards](#rewards)
    - [Rewards Voting](#rewards-voting)
    - [Rewards distribution](#rewards-distribution)
- [Dividends](#dividends)
- [Loans](#loans)


# Boosted Baln
Boosted baln is the locked version of the Balanced token, based on the design of Vote-Escrowed CRV by Curve. 
Boosted Baln is primarily used for three things:
* Your governance power.
* Your share of the Fees earned.
* Increasing your rewards when contributing in for example Liquidity pools.



Boosted baln implements ICONs token interface IRC2 expect for the transfer method, so bBaln can be treated as an IRC2 token but is not transferable.
The balanceOf method is not thus not fixed as other tokens but rather represented as a function over time:
* Every Time you edit your lock, your lock is checkpointed. BalanceOf then calculates you balance from that checkpoint.
* BalanceOf default to using current time to calculate, but can be used to calculate you balance at any time from checkpoint.
* Your locked Baln tokens can only be withdraw in full after the lock has expired. When you bBaln balance is 0.
* Withdrawing early is possible and will penalize you deposited funds before ending your lock and returning funds.

![bBaln](./Resources/bBaln_dark.drawio.svg#gh-dark-mode-only)
![bBaln](./Resources/bBaln_light.drawio.svg#gh-light-mode-only)

BalanceOf is good to use to calculate current and future expected balances but to get past balance it can be incorrect. For this exists balanceOfAt which finds your balance at a specific block. This finds a user checkpoint (closest one before the block) and uses that to calculate similarly to balanceOf.

   
# Rewards
### Rewards Voting
The Rewards contract distribute the Inflation on the BALN token. The distribution are split between platform recipients such as the Daofund and data sources like Loans or Liquidity Pools. Governance contract controls the platform percentages and can also set fixed percentages for sources. The rest are derived from live voting from bBaln holders.

![rewardsVoting](./Resources/RewardsVoting_dark.drawio.svg#gh-dark-mode-only)
![rewardsVoting](./Resources/RewardsVoting_light.drawio.svg#gh-light-mode-only)


### Rewards distribution
The rewards for the different data sources are distributed in a continuous way, based on both your balance in the source and bBaln balance.
Everything that earns you Baln rewards are data sources, which can be anything that has a balance and supply. For Loans we have for example user debt and total debt as balance and supply. While for Liquidity providing it is staked LP tokens and total staked. This means that the rewards contract is not limited to traditional liquidity providing.

Rewards updating are done in 3 steps: 
1. Update Rewards: This mean calculate how much rewards the source should distribute per balance up until the current time. 
2. Accrue the user rewards: Before updating the users balances we use the previous balance to calculate how much rewards the user has earned since last update.
3. Update balances: use up to date data to set and calculate the balances to use for the next update. 

![rewards](./Resources/Rewards_dark.drawio.svg#gh-dark-mode-only)
![rewards](./Resources/Rewards_light.drawio.svg#gh-light-mode-only)

From the flow of rewards we see:
* Your bBaln balance is only updated whenever you interact with a source. 
* Your boost is maximized when you share of the source is the same as your share of bBaln
* TotalWorkingSupply is the sum of balances and can thus be reduces by kicking others in the same source as yourself.



# Dividends
Dividends work very similar to Rewards but the first step is separate:
* Update Dividends: Whenever a fee token is received update the fee amount per balance ratio.
 
   
1. Accrue Dividend: Before updating bBaln claim dividends up until this point with your previous bBaln balance
2. Update bBaln balance: Set the bBaln balance to be used for the next Accrue dividends.
   
![dividends](./Resources/Dividends_dark.drawio.svg#gh-dark-mode-only)
![dividends](./Resources/Dividends_light.drawio.svg#gh-light-mode-only)


# Loans
Loans is the main minter of bnUSD and last resort for stabilizing the peg to a dollar. 

![loans](./Resources/Loans_dark.drawio.svg#gh-dark-mode-only)
![loans](./Resources/Loans_light.drawio.svg#gh-light-mode-only)