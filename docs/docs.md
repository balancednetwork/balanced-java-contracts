### **Balanced DeFi features**

**-> Balanced DEX**

The Balanced DEX (Decentralized Exchange) Smart Contract is a crucial component of the Balanced DeFi platform, providing liquidity pooling, trading, fee management, and rewards distribution. By integrating advanced features such as price oracles, cross-chain interactions, and detailed fee management, the platform ensures a secure, efficient, and user-friendly DeFi experience. The DEX is designed with extensive protections and limits to maintain the stability and integrity of the system, providing users with confidence in their interactions and investments on the platform. This document details the functionalities of the Balanced DEX Smart Contract, outlining its key features and the protections it offers to users in a professional and detailed manner.


#### **Key Functionalities**

1. **Liquidity Pool Management**
    * **Pool Creation and Management**: The DEX allows for the creation and management of liquidity pools. Each pool is identified by a unique ID and consists of a base token and a quote token. Liquidity providers can add liquidity to these pools, facilitating trading between the two tokens.
    * **Adding and Removing Liquidity**: Users can add liquidity to the pools by depositing both base and quote tokens. The system automatically calculates the share of the liquidity pool and issues liquidity pool (LP) tokens representing the user's share. Users can also remove liquidity, withdrawing their share of the pool along with any accrued earnings.
2. **Trading and Market Operations**
    * **Token Swaps**: The DEX facilitates token swaps between the base and quote tokens in a pool. The system calculates the exchange rate based on the current pool balances and applies the necessary fees. Swaps can be performed directly by users or through smart contract interactions.
    * **Market Orders**: Users can place market orders to buy or sell tokens. The DEX processes these orders by matching them with available liquidity in the pools, ensuring efficient and fair trading.
    * **Fee Management**: The DEX supports various fees, including pool LP fees and BALN fees (also known as platform fees). Pool LP fees are credited to liquidity providers, while the BALN fees are distributed to BALN holders. This incentivizes both liquidity provision and holding BALN tokens, ensuring the sustainability of the platform.
3. **Staking and Rewards**
    * **LP Staking**: Liquidity providers can stake their LP tokens to earn rewards. The system tracks the staked amounts and distributes rewards based on the user's share of the total staked LP tokens. Balanced incentivizes specific pools, enhancing liquidity provision in those pools and contributing to the overall stability of the platform.
    * **Earnings Withdrawal**: Users can withdraw their staking earnings at any time. The system calculates the earnings based on the user's staked amount and the reward distribution schedule.
4. **Collateral and Pricing Mechanisms**
    * **Collateral Management**: The DEX allows for the management of collateral tokens, which can be used in various DeFi activities, such as borrowing and lending. The system tracks the total collateral deposited and its current market value.
    * **Price Oracles**: The DEX integrates with price oracles to obtain accurate and up-to-date price information for the tokens in the pools. This ensures fair trading and correct valuation of collateral and LP tokens.
    * **Oracle Protection**: The platform includes mechanisms to protect against oracle manipulation, ensuring the integrity of price feeds and the security of the system.
5. **Cross-Chain Interactions**
    * **Cross-Chain Swaps**: Users can perform token swaps across different chains, leveraging the liquidity available in the DEX pools. The system ensures secure and efficient cross-chain transactions through encoded and decoded message structures via the router contract.
6. **Security and Protections**
    * **Deposit and Withdrawal Protections**: The platform enforces strict controls over deposits and withdrawals, ensuring that user funds are securely managed. Withdrawals can be configured to include specific conditions and permissions, protecting against unauthorized access.
    * **Permission Management**: The system allows for the management of permissions for various actions, ensuring that only authorized users or smart contracts can perform certain operations. This enhances the security and governance of the platform.

### **-> Balanced Loan Feature**


The Balanced Loan Smart Contract is a pivotal component of the Balanced DeFi platform, facilitating borrowing, collateral management, liquidation processes, and cross-chain interactions. This document provides an in-depth description of the functionalities supported by the loan smart contract, highlighting its roles and the protections it offers to users.

The Balanced Loan Smart Contract provides a robust framework for decentralized borrowing and lending, featuring comprehensive collateral management, interest application, liquidation processes, and cross-chain interactions. By implementing stringent protections and dynamic limits, the platform ensures the security and stability of its financial ecosystem, offering users a reliable and flexible DeFi experience.


#### **Key Functionalities**

1. **Borrowing Mechanism**
    * **Taking Loans**: Users can take out loans by providing collateral, currently supported assets include ETH and ICX. The system allows users to borrow the platform's stablecoin, bnUSD, against their deposited collateral. The borrowing process involves specifying the collateral type and the amount to be borrowed.
    * **Repaying Loans**: Borrowers can repay their loans to regain access to their collateral. The contract ensures accurate accounting of the repaid amounts, adjusting the user's debt and collateral balance accordingly.
    * **Reading Positions**: Users and the system can query the status of a loan position, including the amount borrowed, the collateral deposited, and any accrued interest. This functionality is crucial for both transparency and user management of their loans.
2. **Collateral Management**
    * **Depositing Collateral**: Users can deposit supported collateral assets to secure their loans. The system keeps track of the total collateral deposited by each user and ensures it meets the necessary requirements for the loan amount.
    * **Withdrawing Collateral**: Users can withdraw their collateral once their debt is sufficiently reduced or fully repaid. This process includes verifying that the withdrawal does not leave the loan undercollateralized.
    * **Collateral Liquidation**: In the event that a user's loan becomes undercollateralized (i.e., the value of the collateral falls below a certain threshold relative to the loan), the system can trigger a liquidation process. This process involves selling the collateral to repay the loan, thereby protecting the system from bad debt.
3. **Interest and Fees**
    * **Interest Application**: The contract periodically applies interest to the outstanding loans, increasing the debt over time based on predefined interest rates. This functionality ensures that borrowing costs are accurately reflected and accrued.
    * **Claiming Interest**: Users can claim the interest accrued on their deposited collateral or on their loans. This feature allows for the periodic realization of interest earnings.
    * **Fee Management**: The system supports various fees, including origination fees for new loans, redemption fees for repaying collateral, and DAO fees that contribute to the platform's governance fund. These fees are dynamically adjustable to ensure the platform's sustainability and incentivize specific behaviors.
4. **Liquidation and Bad Debt Management**
    * **Liquidation Process**: When a loan's collateral falls below the required threshold, the system can liquidate the collateral to repay the debt. This process involves selling the collateral at market rates to cover the outstanding loan amount.
    * **Bad Debt Redemption**: The platform includes mechanisms to handle bad debt, where the system can retire bad debt by burning equivalent amounts of collateral or by other predefined means. This ensures the integrity and balance of the platform's financial ecosystem.
    * **Liquidation Rewards**: To incentivize the liquidation process, the system may offer rewards to entities that participate in liquidating undercollateralized loans. This helps ensure timely and efficient handling of risky positions.
5. **Cross-Chain Interactions**
    * **Cross-Chain Borrowing**: Users can borrow assets across different chains, leveraging their collateral on one chain to secure loans on another. This functionality is crucial for maintaining liquidity and enabling decentralized finance across multiple blockchain ecosystems.
    * **Cross-Chain Withdrawals**: Similarly, users can withdraw collateral across chains, allowing for flexible management of assets in a multi-chain environment. The system ensures secure and reliable cross-chain transactions through encoded and decoded message structures.

#### **Protections and Limits**

* **Locking and Liquidation Ratios**: The platform enforces strict ratios to determine when a loan becomes undercollateralized. These ratios are adjustable and help maintain the stability of the system by preventing excessive borrowing against insufficient collateral.
* **Debt Ceilings and Interest Rates**: Each supported asset has a predefined debt ceiling and interest rate, limiting the maximum amount that can be borrowed and ensuring predictable borrowing costs. These parameters can be adjusted based on market conditions and platform policies.
* **Maximum Retire Percent**: The system sets a maximum percentage for debt retirement, preventing users from retiring excessive amounts of debt in a single transaction. This ensures a controlled and balanced approach to managing bad debt.

**-> Balanced is a DAO**

Balanced is not just a multi-chain decentralized application (DApp); it also operates as a Decentralized Autonomous Organization (DAO), with its governance driven by BALN token holders. The governance mechanism enables the community to propose, discuss, and vote on changes to the platform, ensuring a decentralized and democratic decision-making process. This document details the functionalities of the Balanced DAO, focusing on governance and the boosted BALN feature, which enhances voting power through token lockup.


#### **Governance Contract**

The governance contract is central to Balanced's DAO operations, providing a robust framework for managing proposals, votes, and administrative actions.



1. **Proposal Management**
    * **Defining Votes**: Any user can define a vote by submitting a proposal, which includes the proposal's name, description, start time, duration, and associated transactions. This process ensures that all proposals are clear and well-documented before being voted on by the community.
    * **Voting and Quorum**: Users cast their votes on proposals, with each vote being recorded and tallied. The governance contract enforces a quorum requirement, ensuring that only proposals with sufficient community participation are executed. The quorum and voting duration limits are configurable to adapt to changing governance needs.
    * **Vote Evaluation**: Once the voting period ends, the results are evaluated to determine if the proposal passes based on the quorum and voting outcomes. Successful proposals are then executed according to the predefined transactions included in the proposal.
2. **Administrative Functions**
    * **Contract Ownership**: The governance contract manages the ownership of other smart contracts within the Balanced ecosystem. It allows for the transfer of contract ownership and the addition of external contracts, ensuring the platform's modularity and adaptability.
    * **Parameter Configuration**: Critical parameters such as vote duration limits, quorum requirements, and vote definition fees are set and managed through the governance contract. These parameters help maintain the integrity and efficiency of the governance process.
    * **Blacklisting and Authorization**: The governance contract includes mechanisms to blacklist addresses and manage authorized callers for shutdown operations, providing a security layer to protect the platform from malicious actors.
3. **Monitoring and Reporting**
    * **Vote Status and History**: The contract offers read-only methods to query the status of votes, voter counts, and historical voting data. This transparency allows the community to track the progress and outcomes of proposals.
    * **Account Positions**: Users can query their voting weight and historical voting records, helping them stay informed about their participation in the governance process.


#### **Boosted BALN Feature**

The boosted BALN feature incentivizes long-term commitment to the Balanced platform by allowing users to lock up their BALN tokens for increased voting power. This mechanism aligns user interests with the platform's long-term success and enhances the governance process.



1. **Token Lockup**
    * **Locking and Unlocking**: Users can lock their BALN tokens for a specified period, boosting their voting power during that time. The longer the lockup period, the greater the boost in voting power. Users can also increase their lockup time or withdraw their tokens once the lockup period ends.
    * **Early Withdrawal**: In cases where users need to access their locked tokens before the lockup period ends, they can withdraw early, though this may incur penalties. The penalty mechanisms are designed to discourage early withdrawal and promote long-term engagement.
2. **Voting Power Calculation**
    * **Boosted Voting Power**: The voting power of locked BALN tokens is calculated based on the lockup duration, giving more influence to users who commit their tokens for longer periods. This mechanism ensures that decisions are made by stakeholders with a vested interest in the platform's future.
    * **User Point History**: The contract maintains a history of user points, which reflects the changes in voting power over time. This historical data helps in auditing and understanding the governance dynamics.
3. **Administrative Functions**
    * **Setting Parameters**: The contract allows for the configuration of various parameters, such as the minimum locking amount and penalty addresses. These settings ensure that the boosted BALN mechanism operates smoothly and aligns with the platform's governance objectives.
    * **Supply and Balance Queries**: Users can query their locked balances, total supply of locked tokens, and their voting power at specific timestamps or blocks. These queries provide transparency and help users manage their governance participation effectively.

**The DAO Fund**

Balanced maintains a DAO fund to ensure the platform's sustainability, reward distribution, and liquidity management. This document provides a detailed and professional overview of the functionalities associated with the DAO fund, highlighting key features and their purposes.


#### **DAO Fund Management Functions**



1. **Delegation and Balances**
    * **Delegate Voting Power**: The DAO fund can delegate its voting power to a set of pre-defined delegations. This is crucial for participating in governance decisions within the ICON network, ensuring that the DAO fund can influence important proposals and network changes.
    * **Get Balances**: This feature retrieves the current balances of the DAO fund in various tokens. It provides a snapshot of the fund’s financial status, offering transparency and enabling better decision-making regarding fund management.
2. **Disbursements**
    * **Get Disbursement Details**: Provides detailed information about the disbursements made to a specific user. It includes the token type, amount, and other relevant details, ensuring transparency and accountability in the disbursement process.
    * **Disburse Tokens**: Facilitates the disbursement of specified tokens from the DAO fund to a recipient address. This is essential for distributing rewards, paying for services, or any other financial transactions that the DAO needs to perform.
    * **Disburse ICX**: Specifically for disbursing ICX tokens to a recipient address, this feature ensures that ICX holdings can be managed and distributed as needed, supporting the operational needs of the DAO.
3. **Claiming Rewards and Fees**
    * **Claim Rewards**: Allows the DAO fund to claim accumulated rewards. These rewards could be from staking or other incentivized activities, contributing to the overall profitability and sustainability of the fund.
    * **Claim Network Fees**: Used to claim network fees accrued by the DAO fund. These fees support the fund's operational expenses and contribute to its financial health.
    * **Claim XCall Fees**: Enables the DAO fund to claim fees associated with cross-chain calls (XCall) on the specified network. This feature ensures that the DAO can benefit from its participation in cross-chain activities.
4. **Liquidity Management**
    * **Supply Liquidity**: This feature allows the DAO fund to provide liquidity to the Balanced DEX by depositing both base and quote assets into a liquidity pool. It helps maintain market stability and enables trading, which is crucial for the platform's operation.
    * **Unstake LP Tokens**: Allows the DAO fund to unstake liquidity provider (LP) tokens from a specific pool. Unstaking LP tokens can be necessary for rebalancing or liquidity adjustments, ensuring optimal fund management.
    * **Withdraw Liquidity**: Used to withdraw liquidity from a specified pool, reducing the fund's exposure or reallocating assets as needed. This flexibility is important for managing the DAO’s financial strategy.
    * **Stake LP Tokens**: Enables the staking of LP tokens into a specified pool, allowing the DAO fund to earn rewards and fees from liquidity provision. This feature supports the fund's earning potential and contributes to its growth.
5. **Proof of Liquidity (POL) Management**
    * **Set POL Supply Slippage**: This feature sets the slippage tolerance for providing liquidity, ensuring that transactions occur within acceptable price ranges to minimize losses. It is essential for risk management.
    * **Get POL Supply Slippage**: Retrieves the current slippage tolerance setting for providing liquidity, providing transparency and insight into the fund’s operational parameters.
    * **Set XCall Fee Permission**: Grants permission for a contract to handle XCall fees on a specific network, ensuring that only authorized contracts can process these fees. This is critical for maintaining security and proper management of cross-chain interactions.
    * **Get XCall Fee Permission**: Checks if a specific contract has permission to handle XCall fees on a given network, ensuring compliance and proper authorization within the ecosystem.
6. **Earnings and Financial Reporting**
    * **Get BALN Earnings**: Retrieves the total BALN token earnings accrued by the DAO fund, providing insight into the fund's performance and reward accumulation. This transparency supports informed decision-making and governance.
    * **Get Fee Earnings**: Returns a breakdown of fee earnings in various tokens, contributing to the financial transparency of the DAO fund. It allows stakeholders to understand the sources of income and the financial health of the fund.