# Balanced and xCall


Balanced supports cross-chain interactions, facilitating seamless asset and data transfers between different blockchain networks. This functionality is underpinned by a hub-and-spoke design, wherein all cross-chain communication, facilitated by the xCall protocol, either originates from or terminates at the ICON hub for all Balanced logic. The spokes, or individual blockchain networks, do not communicate directly with each other.

The integration with xCall enables efficient cross-chain communication by transporting Balanced messages between chains. Before a Balanced message is sent to xCall on the source chain, it is encoded using the Recursive Length Prefix (RLP) encoding protocol, a standard encoding method. Upon reaching the destination chain, the Balanced DApp decodes the RLP-encoded message and processes it accordingly. This ensures that messages are securely and accurately transmitted between different blockchain networks, maintaining the integrity and functionality of the Balanced platform across multiple chains.

****
- [Balanced and xCall](#balanced-and-xcall)
- [How Balanced Uses xCall](#how-balanced-uses-xcall)
- [What Contracts Use xCall?](#what-contracts-use-xcall)
  - [1. Asset Manager Contract](#1-asset-manager-contract)
    - [Configuration and Validation](#configuration-and-validation)
    - [Rate Limits](#rate-limits)
    - [Token Deposits and Cross-Chain Transactions](#token-deposits-and-cross-chain-transactions)
    - [Withdrawals](#withdrawals)
    - [Utilization of Tokens in the Balanced Platform](#utilization-of-tokens-in-the-balanced-platform)
  - [2. Balanced Dollar Contract](#2-balanced-dollar-contract)
    - [Configuration and Validation](#configuration-and-validation-1)
    - [Cross-Chain Transfer and Withdrawal Mechanism](#cross-chain-transfer-and-withdrawal-mechanism)
  - [3. xCall Manager Contract](#3-xcall-manager-contract)
    - [Configuration and Validation](#configuration-and-validation-2)
    - [Protocol Management](#protocol-management)
- [Cross-Chain Message Structures in Balanced](#cross-chain-message-structures-in-balanced)
  - [Asset Manager Contract](#asset-manager-contract)
  - [Balanced Dollar Contract](#balanced-dollar-contract)
  - [xCall Manager Contract](#xcall-manager-contract)
- [Methods Involved in Cross-Chain Communications in Balanced](#methods-involved-in-cross-chain-communications-in-balanced)
  - [Key Contracts and Methods](#key-contracts-and-methods)
  - [Asset Manager Contract](#asset-manager-contract-1)
  - [Balanced Dollar Contract](#balanced-dollar-contract-1)
  - [xCall Manager Contract](#xcall-manager-contract-1)
- [Supported Blockchains on Balanced](#supported-blockchains-on-balanced)
- [Contract Details on Each Blockchain](#contract-details-on-each-blockchain)
  - [ICON (Hub)](#icon-hub)
    - [ICON Asset Manager Contract](#icon-asset-manager-contract)
    - [Balanced Dollar Contract](#balanced-dollar-contract-2)
    - [Governance Contract](#governance-contract)
  - [EVM-Compatible Blockchains (Ethereum, Binance Smart Chain, Avalanche, Base, Arbitrum)](#evm-compatible-blockchains-ethereum-binance-smart-chain-avalanche-base-arbitrum)
  - [Cosmos-Related Blockchains (Archway, Injective)](#cosmos-related-blockchains-archway-injective)
  - [SUI](#sui)
  - [Stellar](#stellar)
  - [Solana](#solana)
# How Balanced Uses xCall

xCall is integral to Balanced’s cross-chain communication capabilities. It serves as the cross-chain messaging protocol that enables Balanced to transmit and receive messages between different blockchain networks. The protocol employs Recursive Length Prefix (RLP) encoding as its underlying mechanism to ensure standardized and efficient message encoding.

In the Balanced architecture, xCall Manager contracts are deployed on each spoke chain. These contracts are responsible for configuring and verifying the protocols specified by xCall, ensuring the integrity of cross-chain communications. Specifically, they manage the addresses of the intermediary contracts that xCall uses to facilitate these interactions.

The governance contract on the ICON (Hub) blockchain plays a critical role in the management of xCall protocols. It is responsible for setting and updating the protocols in the spoke chains, including the intermediary contract addresses that xCall will utilize. The governance contract uses xCall to propagate these protocol settings to the spoke chains, ensuring consistency and proper configuration across the entire network.

# What Contracts Use xCall?

There are mainly three contracts that exist on all the Balanced spoke chains:

## 1. Asset Manager Contract

The spoke Asset Manager contract in the Balanced ecosystem plays a critical role in facilitating secure and efficient cross-chain transactions. By integrating with the ICON Asset Manager and the xCall Manager, it ensures smooth token deposits, withdrawals, and overall management of user assets. The contract's configuration and validation mechanisms, along with the rate limit feature, enhance the security and functionality of the Balanced platform.

### Configuration and Validation

In the spoke Asset Manager contract, the ICON (hub) Asset Manager contract is set as an authorized entity. This configuration allows the spoke Asset Manager to send messages to the ICON Asset Manager contract. Additionally, the spoke Asset Manager is capable of validating incoming messages to ensure they originate from the ICON Asset Manager contract.

The spoke Asset Manager contract is also configured to interact with the spoke xCall Manager contract. This setup allows it to obtain the necessary protocols for performing cross-chain transactions via xCall. The xCall configuration within the Asset Manager ensures that it can identify xCall requests and validate messages received through xCall.

### Rate Limits

The Asset Manager contract includes a rate limit feature, which is used to configure limits for each token deposited in Balanced. This ensures that a specified amount of each token must remain in the spoke Asset Manager contract, preventing excessive withdrawals and maintaining liquidity.

### Token Deposits and Cross-Chain Transactions

The Asset Manager contract on the spoke chain accepts deposits of various tokens, including native tokens. Upon receiving a deposit, it locks the tokens within the contract. Subsequently, it sends a deposit message to the ICON (hub) Asset Manager contract via xCall. The ICON Asset Manager processes this message and mints an equivalent amount of wrapped tokens for the user on the ICON chain.

In the event that the cross-chain transaction fails at any point, a rollback transaction is initiated to refund the deposited tokens to the user.

### Withdrawals

Users holding wrapped tokens on the ICON chain can initiate a withdrawal transaction. These wrapped tokens are treated as the real tokens within the Balanced platform. When a user requests a withdrawal, the ICON Asset Manager burns the wrapped tokens and sends a message to the spoke Asset Manager contract. The spoke Asset Manager then releases the corresponding amount of native tokens back to the user.

### Utilization of Tokens in the Balanced Platform

Users with token balances from any chain within the Balanced platform can engage in various activities offered by the platform, such as swaps, cross-chain swaps, loans, and liquidity provision.

## 2. Balanced Dollar Contract

Balanced stablecoin, bnUSD, will be deployed as a native stablecoin on each spoke chain. To facilitate this, a dedicated Balanced Dollar contract will be implemented on each spoke chain. The integration of bnUSD across spoke chains enhances the liquidity and functionality of the Balanced ecosystem. By utilizing a burn and mint mechanism facilitated by xCall, users can seamlessly transfer and withdraw bnUSD between the ICON (Hub) and spoke chains. The careful configuration of the Balanced Dollar contracts ensures secure and efficient cross-chain operations, allowing users to leverage bnUSD for various financial activities across different blockchain networks.

### Configuration and Validation

In the Balanced Dollar contract on each spoke chain, the ICON (Hub) Balanced Dollar contract is set as an authorized entity. This configuration allows the spoke Balanced Dollar contract to send messages to the ICON Balanced Dollar contract and validate incoming messages to ensure they originate from the ICON contract.

The spoke Balanced Dollar contract is also configured to interact with the xCall Manager contract. This setup allows it to obtain the necessary protocols for performing cross-chain transactions via xCall. The xCall configuration within the Balanced Dollar contract ensures that it can identify xCall requests and validate messages received through xCall.

### Cross-Chain Transfer and Withdrawal Mechanism

Balanced employs a burn and mint mechanism for handling cross-chain transfers and withdrawals of bnUSD:

1. **Cross-Chain Transfer:**
    - When a user initiates a cross-chain transfer in the spoke Balanced Dollar contract (i.e., transferring bnUSD to the ICON chain), the user's bnUSD balance on the spoke chain is burned.
    - xCall is used to pass a cross-transfer message to the ICON Balanced Dollar contract.
    - Upon receiving the encoded message, the respective Balanced Dollar contract on the ICON chain decodes it and mints an equivalent amount of bnUSD for the user on the ICON chain.

2. **Withdrawal:**
    - When a user wishes to convert ICON bnUSD back to the spoke chain's bnUSD, they initiate a withdrawal request on the ICON chain.
    - The ICON Balanced Dollar contract burns the user's bnUSD amount on the ICON chain.
    - A message is sent via xCall to the spoke Balanced Dollar contract.
    - The spoke Balanced Dollar contract receives the message, verifies it, and mints the equivalent amount of bnUSD for the user on the spoke chain.

In the event that a cross-chain transaction fails at any point, a rollback transaction is initiated to refund the bnUSD to the user.

## 3. xCall Manager Contract

The xCall Manager contract is a crucial component in the Balanced ecosystem, responsible for managing and facilitating cross-chain communications. It ensures that messages and transactions between different blockchain networks are secure, validated, and properly routed.

### Configuration and Validation

In the xCall Manager contract, the ICON governance contract address is configured to enable identification and validation of the ICON governance contract. Similarly, the spoke xCall address is configured within the xCall Manager for the same purpose. This configuration ensures secure and verified communication between the ICON and spoke chain contracts.

### Protocol Management

The ICON governance contract plays a crucial role in managing the protocols used for cross-chain communication:

- **Setting Protocols:**
    - The ICON governance contract is responsible for setting the protocols in the spoke xCall Manager contract.
    - These protocols are essential for ensuring consistent and secure cross-chain interactions.

- **Removing and Modifying Protocols:**
    - Protocols can also be removed or updated in the xCall Manager contract as needed.
    - The modified protocols can be retrieved through an API, ensuring that all components of the network are updated with the latest configurations.

- **Protocol Utilization:**
    - The spoke Asset Manager and the spoke Balanced Dollar contracts use the same protocols that are configured on the xCall Manager contract, ensuring proper and clear cross-chain communication.




# Cross-Chain Message Structures in Balanced

Balanced is a multi-chain decentralized application (DApp) designed with a hub-and-spoke architecture, supporting various programming languages for smart contracts across different blockchains. To ensure efficient, secure, and reliable cross-chain transactions, messages transferred between the hub (ICON) and spokes must adhere to specific structures. These messages, transmitted via xCall, are encoded on one side and decoded on the other, maintaining consistency in data structure across all chains.

Regardless of the blockchain's external methods, the message structures must remain uniform. This consistency ensures that the ICON hub receives messages in the same format from any spoke chain, and vice versa. Below are the defined message structures used for cross-chain communication in the Balanced platform:

## Asset Manager Contract

1. **Deposit**
   This structure is used to deposit tokens from a spoke chain to the ICON hub.

    **Data Structure:**
    ```
    [
        "Deposit",
        Address <Token>,
        Address <Depositor>,
        NetworkAddress <Receiver>,
        Number <Amount>,
        Bytes <Data>
    ]
    ```


2. **Deposit Revert**
    This structure handles reverting a deposit if the cross-chain transaction fails.

    **Data Structure:**
    ```
    [
        "DepositRevert",
        Address <Token>,
        Address <Receiver>,
        Number <Amount>
    ]
    ```


3. **Withdraw To**
    This structure is used for withdrawing tokens from the ICON hub to a spoke chain.

    **Data Structure:**
    ```
    [
        "WithdrawTo",
        Address <Token>,
        NetworkAddress <Receiver>,
        Number <Amount>
    ]
    ```

## Balanced Dollar Contract

1. **Cross Transfer**
    This structure is used for transferring bnUSD across chains.

    **Data Structure:**
    ```
    [
        "CrossTransfer",
        Address <Token>,
        Address <Sender>,
        NetworkAddress <Receiver>,
        Number <Amount>
    ]
    ```
2. **Cross Transfer Revert**
    This structure handles reverting a cross transfer if the transaction fails.

    **Data Structure:**
    ```
    [
        "CrossTransferRevert",
        Address <Token>,
        Address <Receiver>,
        Number <Amount>
    ]
    ```

## xCall Manager Contract

1. **Configure Protocol**

    This structure is used to configure protocols in the xCall Manager.

    **Data Structure:**
    ```
    [
        "ConfigureProtocol",
        String[] <Source protocols>,
        String[] <Destination protocols>
    ]
    ```

2. **Execute**

    This structure is used to execute an arbitrary method as defined in the ExecutionData. As arbitrary method calls are not supported in some languages like SUI and Stellar, this is not included in those chains.

    **Data Structure:**
    ```
    [
        "Execute",
        Address <Manager>,
        NetworkAddress <ProtocolAddress>,
        Bytes <ExecutionData>
    ]
    ```

Consistent message structures are critical for maintaining the integrity and reliability of cross-chain transactions within the Balanced ecosystem. By adhering to these standardized formats, the platform ensures smooth and secure communication between the ICON hub and various spoke chains, regardless of their underlying programming languages or external methods.secure communication between the ICON hub and various spoke chains, regardless of their underlying programming languages or external methods.

# Methods Involved in Cross-Chain Communications in Balanced

Cross-chain communication in the Balanced ecosystem involves multiple blockchains, each potentially supporting different programming languages for their smart contracts. Due to the inherent differences in language design, it is impractical to maintain identical external method signatures across all chains. Nevertheless, the Balanced architecture is designed to facilitate seamless cross-chain interactions, ensuring that while method signatures may vary, the core functionalities remain consistent.

## Key Contracts and Methods

The primary contracts involved in cross-chain communications within Balanced are the Asset Manager, Balanced Dollar, and xCall Manager contracts. Below is a detailed overview of the common methods used in these contracts for cross-chain operations.

## Asset Manager Contract

1. **Deposit**
The "deposit" method is used to deposit user balances from a spoke chain to the ICON hub.

* **Method Name:** `deposit`
* **Purpose:** To deposit the user's balance.
* **Typical Signature:**
  ```solidity
  external deposit(
      address token,
      uint amount,
      string memory to,
      bytes memory data
  )
  ```
* **Description:** This method facilitates the transfer of tokens from the user’s account on the spoke chain to the Asset Manager contract. It includes parameters for the token address, the amount to be deposited, the recipient's network address as well as optional transaction data.

1. **Handle Call Message**
The "handleCallMessage" method handles messages from the ICON hub's Asset Manager contract. This includes processing withdrawal messages from the ICON Asset Manager contract and handling deposit revert messages from the spoke xCall.

* **Method Name:** `handleCallMessage`
* **Purpose:** To handle messages from the ICON hub's Asset Manager contract.
* **Typical Signature:**
  ```solidity
  external handleCallMessage(
      string calldata from,
      bytes calldata data,
      string[] calldata protocols
  )
  ```
* **Description:** This method is invoked to process cross-chain messages, such as withdrawal requests from the ICON Asset Manager contract and deposit reverts. It validates the sender's address, decodes the message data, and applies the necessary protocols to complete the transaction.

## Balanced Dollar Contract

1. **Cross Transfer**
The "crossTransfer" method enables the transfer of bnUSD stablecoin across chains.

* **Method Name:** `crossTransfer`
* **Purpose:** To facilitate cross-chain transfer of bnUSD stablecoin.
* **Typical Signature:**
  ```solidity
  external crossTransfer(
      string memory to,
      uint value
  )
  ```
* **Description:** This method handles the burning of bnUSD on the originating spoke chain and sends a message to the ICON hub to mint an equivalent amount of bnUSD for the recipient.

2. **Handle Call Message**
Similar to the Asset Manager, the "handleCallMessage" method in the Balanced Dollar contract processes messages from the ICON hub, including handling cross-transfer reverts.

* **Method Name:** `handleCallMessage`
* **Purpose:** To handle messages from the ICON hub's Balanced Dollar contract.
* **Typical Signature:**
  ```solidity
  external handleCallMessage(
      string calldata from,
      bytes calldata data,
      string[] calldata protocols
  )
  ```
* **Description:** This method processes messages related to cross-chain bnUSD transfers, including managing reverts in case of transaction failures. It ensures that any cross-chain communication is correctly interpreted and executed.

## xCall Manager Contract

1. **Handle Call Message**
The `handleCallMessage` method in the xCall Manager contract processes cross-chain protocol configuration messages as dictated by the ICON governance contract.

* **Method Name:** `handleCallMessage`
* **Purpose:** To handle messages related to protocol configuration from the ICON governance contract.
* **Typical Signature:**
  ```solidity
  external handleCallMessage(
      string calldata from,
      bytes calldata data,
      string[] calldata protocols
  )
  ```
* **Description:** This method allows the xCall Manager contract to set up and update the necessary protocols for cross-chain transactions. It ensures that the protocols are correctly configured and disseminated to the relevant contracts.

# Supported Blockchains on Balanced

Balanced currently supports all blockchains that are compatible with the xCall protocol. ICON serves as the hub, maintaining a record of balances for all tokens held by users across various spoke chains.

Balanced's support for a wide range of blockchains through the xCall protocol enables robust cross-chain functionality and interoperability. The carefully designed smart contracts, tailored to the specific languages and frameworks of each blockchain, ensure secure and efficient operations across the entire ecosystem. By leveraging the strengths of each blockchain's technology stack, Balanced provides a seamless and integrated experience for users across multiple networks. The supported spoke chains are:

- Archway
- Avalanche
- Injective
- Ethereum
- Binance Smart Chain (BSC)
- Base
- Arbitrum
- SUI
- Stellar
- Solana


# Contract Details on Each Blockchain

Balanced utilizes a diverse codebase tailored to the programming languages and frameworks prevalent on each supported blockchain. The details of the smart contracts on each blockchain are as follows:

## ICON (Hub)

- **Programming Language:** Java
- **Role:** Acts as the central hub, managing and tracking the balances of all tokens for users from every spoke chain. It facilitates cross-chain transactions and ensures the integrity and consistency of data across the network.

In the ICON ecosystem, the key contracts involved in cross-chain transactions are the Asset Manager, Balanced Dollar, and Governance contracts. This section provides a detailed overview of these contracts and their roles.

### ICON Asset Manager Contract

The ICON Asset Manager contract is integral to managing cross-chain transactions and interacting with spoke asset manager contracts.

1. **Interaction with Spoke Asset Manager Contracts:**
    * The ICON Asset Manager identifies and validates all spoke asset manager contracts.
    * This involves a process called "adding spoke manager," where spoke asset manager addresses are registered.
    * Since the same address can exist on different chains, addresses are restructured into a format known as the "network address" within the xCall protocol. For more details on this format, refer to the "Network Address" section.

2. **Handling Deposits and Withdrawals:**
    * The ICON Asset Manager handles deposits from all spoke chains and the ICON chain itself.
    * It also processes withdrawals to all spoke chains and the ICON chain.

3. **Decimal Representation Management:**
    * The contract manages the conversion of different blockchain token decimals.
    * All ICON tokens are standardized to 18 decimal places.
    * Tokens with different decimal places are converted to 18 decimal places upon arrival in ICON and reverted to their original decimal places upon departure from ICON.

4. **Setting Up Spoke Chains:**
    * For each spoke asset, a new asset contract is deployed using the ICON Asset Manager contract, serving as the wrapped version of the asset.
    * During the setup of a spoke chain, a new asset contract for the native token of the spoke chain is deployed.
    * Various configuration activities are performed when setting up a spoke chain, including:
        * Deploying a New Asset Contract for the native token of the spoke chain.
        * Adding Spoke Manager: Registering the spoke asset manager.
        * Adding Chain to Balanced Dollar: Integrating the new chain into the Balanced Dollar system.
        * Setting xCall Fee Permission: Configuring fee permissions for multiple contracts such as loans, routing, DAO Fund, and Balanced Dollar.

### Balanced Dollar Contract

The Balanced Dollar contract manages the bnUSD stablecoin across different chains, ensuring seamless cross-chain transactions and maintaining stability.

1. **Cross-Chain Transfers:**
    * Utilizes a burn-and-mint mechanism for transferring bnUSD between ICON and spoke chains.
    * When bnUSD is transferred to ICON, it is burned on the spoke chain and minted on ICON.
    * When bnUSD is withdrawn from ICON, it is burned on ICON and minted on the spoke chain.

2. **Protocol Utilization:**
    * The protocols set by the ICON Governance contract are utilized by the Balanced Dollar contract to ensure secure and validated transactions.

### Governance Contract

The Governance contract oversees the configuration and management of protocols for cross-chain transactions, ensuring that the ecosystem operates smoothly and securely.

1. **Protocol Management:**
    * Sets, updates, and removes protocols for the xCall Manager contract.
    * Ensures that all protocols are distributed to and utilized by the relevant contracts, including the Asset Manager and Balanced Dollar contracts.

2. **Validation and Security:**
    * Validates all cross-chain transactions to maintain the integrity of the network.
    * Ensures that all interactions between ICON and spoke chains adhere to the established protocols.

## EVM-Compatible Blockchains (Ethereum, Binance Smart Chain, Avalanche, Base, Arbitrum)

* **Programming Language:** Solidity
* **Role:** Implements the same set of smart contracts across all EVM-compatible blockchains, ensuring uniform functionality and interoperability. These contracts handle token operations, cross-chain communication, and interaction with the xCall protocol.


## Cosmos-Related Blockchains (Archway, Injective)

* **Programming Language:** Rust
* **Role:** Utilizes Rust for the development of smart contracts, ensuring robust and secure operations. These contracts are designed to manage token transactions and facilitate cross-chain activities using the xCall protocol.

## SUI

* **Programming Language:** SUI Move (a framework of Rust)
* **Role:** The smart contracts on SUI are written in the SUI Move framework, leveraging Rust's safety and performance. These contracts manage token operations and cross-chain interactions specific to the SUI ecosystem.

**Changes in the implementation of Balanced on SUI**

**Overview**

This document outlines the major changes in the implementation of Balanced in the Sui blockchain and the rationale behind these changes. Key updates include modifications to the execution process of calls and rollbacks, the introduction of forced rollback mechanisms, Token registration, Token contract of balanced dollar split from Balanced dollar contract.

1. **Execution of incoming calls and rollbacks**

   **Change:** The regular flow for the incoming messages that xCall calls the Balanced `handleCallMessage` method is replaced with the new flow: Balanced `execute_call` and `execute_rollback` methods call the xCall `execute_call` method.

   **Process:**
   1. Balanced each contract registers as a dApp to the xCall and receives the registration ID while configured.
   2. **Initiate Call from Balanced:** Balanced initiates a call to xCall with the registration ID and receives a ticket containing execution data and protocol information.
   3. **Retrieve Execution Data:** Balanced retrieves execution data from the ticket.
   4. **Execute Call within Balanced:** Balanced executes the call using its own state and data.
   5. **Report Execution Result:**
      * If successful, Balanced sends `true` to `execute_call_result` in xCall.
      * If failed, Balanced sends `false` to `execute_call_result` in xCall.

   **Rationale:**
   * Sui is a stateless blockchain, meaning it does not maintain the state of every dApp.
   * Executing calls from xCall would require accessing the data of each dApp, which is inefficient.
   * By executing calls from the dApps, each dApp has its own data and uses a common xCall, making the process more efficient and reducing the data management overhead for xCall.

2. **Handling Rollback Failures**

   **Change:** Introduced `execute_forced_rollback` in Balanced, which can be executed by an admin in case of a failure in `execute_call`.

   **Rationale:** There is no concept of exception handling in Sui, such as try-catch, making it impossible to rollback every message that fails in `execute_call`. Instead, it will fail the entire transaction if there is a configuration failure.

3. **Token Registration for Deposit**

   **Change:** Added a new feature to register the Sui tokens to be accepted in the Balanced platform.

   **Rationale:** Sui tokens implement the UTXO model, which involves transferring the actual coin rather than the value of the coin in Sui while depositing on Balanced. The coin needs to be identified by the contract to accept it. For this purpose, the coin is registered priorly.

4. **Balanced Dollar Token Contract Separation**

   **Change:** Token contract for Balanced Dollar is separated from the cross-chain features.

   **Rationale:** For each upgrade of the contract, SUI creates a new ID, and each new and old ID is accessible for communication. It is irrelevant to have multiple IDs for a token, and it's better for token contracts to be immutable. Therefore, the Balanced Dollar token contract is separated to avoid future upgrades, while the Balanced Dollar cross-chain contract can be upgraded in the future.

5. **Version Upgrade Feature**

   **Change:** Version upgrade feature is added to each Balanced contract except the Token contract.

   **Rationale:** Contract communication on the SUI blockchain is possible using each contract ID after upgrades. To restrict the communication to the latest upgrade only, the Balanced version upgrade feature is implemented.

## Stellar

* **Programming Language:** Soroban (a framework of Rust)
* **Role:** Stellar contracts are written using the Soroban framework, designed for the Stellar network. These contracts handle token transactions and cross-chain communications, ensuring seamless integration with the Balanced ecosystem.

## Solana

* **Programming Language:** Rust (Solana framework)
* **Role:** Solana smart contracts are developed in Rust using the Solana framework. They manage token activities and cross-chain interactions, ensuring efficient and secure operations within the Solana network.
