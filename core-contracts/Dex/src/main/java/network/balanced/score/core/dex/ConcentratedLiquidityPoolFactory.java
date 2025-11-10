/*
 * Copyright (c) 2024 Balanced.network.
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

package network.balanced.score.core.dex;

import java.math.BigInteger;
import network.balanced.score.core.dex.models.ConcentratedLiquidityPoolDeployer;
import network.balanced.score.core.dex.interfaces.pooldeployer.IConcentratedLiquidityPoolDeployer;
import network.balanced.score.core.dex.structs.factory.Parameters;
import network.balanced.score.core.dex.interfaces.pool.IConcentratedLiquidityPool;
import network.balanced.score.core.dex.utils.AddressUtils;
import network.balanced.score.core.dex.utils.EnumerableSet;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;

/**
 * @title Canonical Balanced factory
 * @notice Deploys Balanced pools and manages ownership and control over pool protocol fees
 */
public class ConcentratedLiquidityPoolFactory implements IConcentratedLiquidityPoolDeployer {

    // ================================================
    // Consts
    // ================================================
    
    // Contract class name
    private static final String NAME = "ConcentratedLiquidityPoolFactory";

    // Contract name
    private final String name;

    // ================================================
    // DB Variables
    // ================================================
    protected final VarDB<Address> owner = Context.newVarDB(NAME + "_owner", Address.class);
    protected final DictDB<Integer, Integer> feeAmountTickSpacing = Context.newDictDB(NAME + "_feeAmountTickSpacing", Integer.class);
    protected final BranchDB<Address, BranchDB<Address, DictDB<Integer, Address>>> getPool = Context.newBranchDB(NAME + "_getPool", Address.class);
    protected final VarDB<byte[]> poolContract = Context.newVarDB(NAME + "_poolContract", byte[].class);
    protected final EnumerableSet<Address> poolsSet = new EnumerableSet<Address>(NAME + "_poolsSet", Address.class);

    // Implements IConcentratedLiquidityPoolDeployer
    private final ConcentratedLiquidityPoolDeployer poolDeployer;

    // ================================================
    // Event Logs
    // ================================================
    /**
     * @notice Emitted when the owner of the factory is changed
     * @param oldOwner The owner before the owner was changed
     * @param newOwner The owner after the owner was changed
     */
    @EventLog(indexed = 2)
    public void OwnerChanged(
        Address zeroAddress, 
        Address caller
    ) {}

    /**
     * @notice Emitted when a new fee amount is enabled for pool creation via the factory
     * @param fee The enabled fee, denominated in hundredths of a bip
     * @param tickSpacing The minimum number of ticks between initialized ticks for pools created with the given fee
     */
    @EventLog(indexed = 2)
    public void FeeAmountEnabled(
        int fee, 
        int tickSpacing
    ) {}

    @EventLog(indexed = 3)
    public void PoolCreated(
        Address token0, 
        Address token1, 
        int fee, 
        int tickSpacing, 
        Address pool
    ) {}

    @EventLog(indexed = 3)
    public void PoolUpdated(
        Address token0, 
        Address token1, 
        int fee, 
        int tickSpacing, 
        Address pool
    ) {}

    // ================================================
    // Methods
    // ================================================
    /**
     *  Contract constructor
     */
    public ConcentratedLiquidityPoolFactory() {
        this.poolDeployer = new ConcentratedLiquidityPoolDeployer();

        final Address caller = Context.getCaller();
        this.name = "Balanced Factory";

        // Default values during deployment
        Context.println(this.owner.toString());
        if (this.owner.get() == null) {
            this.owner.set(caller);
            this.OwnerChanged(AddressUtils.ZERO_ADDRESS, caller);
        }

        if (this.feeAmountTickSpacing.get(500) == null) {
            this.feeAmountTickSpacing.set(500, 10);
            this.FeeAmountEnabled(500, 10);
        }

        if (this.feeAmountTickSpacing.get(3000) == null) {
            this.feeAmountTickSpacing.set(3000, 60);
            this.FeeAmountEnabled(3000, 60);
        }

        if (this.feeAmountTickSpacing.get(10000) == null) {
            this.feeAmountTickSpacing.set(10000, 200);
            this.FeeAmountEnabled(10000, 200);
        }
    }

    /**
     * @notice Creates a pool for the given two tokens and fee
     * 
     * Access: Everyone
     * 
     * @param tokenA One of the two tokens in the desired pool
     * @param tokenB The other of the two tokens in the desired pool
     * @param fee The desired fee for the pool
     * @dev tokenA and tokenB may be passed in either order: token0/token1 or token1/token0. tickSpacing is retrieved
     * from the fee. The call will revert if the pool already exists, the fee is invalid, or the token arguments
     * are invalid.
     * @return pool The address of the newly created pool
     */
    @External
    public Address createPool (
        Address tokenA,
        Address tokenB,
        int fee
    ) {
        // Checks
        Context.require(!tokenA.equals(tokenB),
            "createPool: tokenA must be different from tokenB");

        Address token0 = tokenA;
        Address token1 = tokenB;

        // Make sure tokens addresses are ordered
        if (AddressUtils.compareTo(tokenA, tokenB) >= 0) {
            token0 = tokenB;
            token1 = tokenA;
        }

        Context.require(!token0.equals(AddressUtils.ZERO_ADDRESS),
            "createPool: token0 cannot be ZERO_ADDRESS");

        int tickSpacing = this.feeAmountTickSpacing.getOrDefault(fee, 0);
        Context.require(tickSpacing != 0, 
            "createPool: tickSpacing cannot be 0");

        Context.require(getPool.at(token0).at(token1).get(fee) == null, 
            "createPool: pool already exists");

        // OK
        Address pool = this.poolDeployer.deploy (
            this.poolContract.get(), 
            Context.getAddress(), 
            token0, token1, fee, tickSpacing
        );

        this.getPool.at(token0).at(token1).set(fee, pool);
        // populate mapping in the reverse direction, deliberate choice to avoid the cost of comparing addresses
        this.getPool.at(token1).at(token0).set(fee, pool);
        // Add to the global pool list
        this.poolsSet.add(pool);

        this.PoolCreated(token0, token1, fee, tickSpacing, pool);

        return pool;
    }

    /**
     * @notice Update an existing pool contract given a pool address
     * 
     * Access: Owner
     * 
     * @param pool An existing pool address
     */
    @External
    public void updatePool (
        Address pool
    ) {
        // Access control
        checkOwner();

        // OK
        Address token0 = IConcentratedLiquidityPool.token0(pool);
        Address token1 = IConcentratedLiquidityPool.token1(pool);
        int fee = IConcentratedLiquidityPool.fee(pool);
        int tickSpacing = IConcentratedLiquidityPool.tickSpacing(pool);

        this.poolDeployer.update (
            pool,
            this.poolContract.get(),
            Context.getAddress(),
            token0, token1, fee, tickSpacing
        );

        this.PoolUpdated(token0, token1, fee, tickSpacing, pool);
    }

    /**
     * @notice Updates the owner of the factory
     * 
     * Access: Owner
     * 
     * @dev Must be called by the current owner
     * @param _owner The new owner of the factory
     */
    @External
    public void setOwner (
        Address _owner
    ) {
        // Access control
        checkOwner();

        // OK
        Address currentOwner = this.owner.get();
        this.OwnerChanged(currentOwner, _owner);
        this.owner.set(_owner);
    }

    /**
     * @notice Enables a fee amount with the given tickSpacing
     * 
     * Access: Owner
     * 
     * @dev Fee amounts may never be removed once enabled
     * @param fee The fee amount to enable, denominated in hundredths of a bip (i.e. 1e-6)
     * @param tickSpacing The spacing between ticks to be enforced for all pools created with the given fee amount
     */
    @External
    public void enableFeeAmount (
        int fee, 
        int tickSpacing
    ) {
        // Access control
        checkOwner();

        // Checks
        Context.require(fee < 1_000_000, 
            "enableFeeAmount: fee needs to be lower than 1,000,000");

        // tick spacing is capped at 16384 to prevent the situation where tickSpacing is so large that
        // TickBitmap#nextInitializedTickWithinOneWord overflows int24 container from a valid tick
        // 16384 ticks represents a >5x price change with ticks of 1 bips
        Context.require(tickSpacing > 0 && tickSpacing < 16384,
            "enableFeeAmount: tickSpacing > 0 && tickSpacing < 16384");

        Context.require(this.feeAmountTickSpacing.get(fee) == 0,
            "enableFeeAmount: fee amount is already enabled");

        // OK
        this.feeAmountTickSpacing.set(fee, tickSpacing);
        this.FeeAmountEnabled(fee, tickSpacing);
    }

    /**
     * Set the pool contract bytes to be newly deployed with `createPool`
     * 
     * Access: Owner
     * 
     * @param contractBytes
     */
    @External
    public void setPoolContract (
        byte[] contractBytes
    ) {
        // Access control
        checkOwner();

        // OK
        this.poolContract.set(contractBytes);
    }

    // ================================================
    // Checks
    // ================================================
    private void checkOwner () {
        Address currentOwner = this.owner.get();
        Context.require(Context.getCaller().equals(currentOwner),
            "checkOwner: caller must be owner");
    }

    // ================================================
    // Public variable getters
    // ================================================
    /**
     * Get the contract name
     */
    @External(readonly = true)
    public String name() {
        return this.name;
    }

    /**
     * Get the current owner of the Factory
     */
    @External(readonly = true)
    public Address owner() {
        return this.owner.get();
    }

    /**
     * Get the current pool contract bytes of the Factory
     */
    @External(readonly = true)
    public byte[] poolContract () {
        return this.poolContract.get();
    }

    /**
     * Get the deployed pools list size
     */
    @External(readonly = true)
    public BigInteger poolsSize() {
        return BigInteger.valueOf(this.poolsSet.length());
    }

    /**
     * Get a deployed pools list item
     * @param index the index of the item to read from the deployed pools list
     * @return The pool address
     */
    @External(readonly = true)
    public Address pools (
        int index
    ) {
        return this.poolsSet.get(index);
    }

    /**
     * Check if the pool exists in the Factory
     * @param pool A pool address
     * @return True if exists, false otherwise
     */
    @External(readonly = true)
    public boolean poolExists (
        Address pool
    ) {
        return this.poolsSet.contains(pool);
    }

    /**
     * Get a deployed pool address from its parameters
     * The `token0` and `token1` parameters can be inverted, it will return the same pool address
     * 
     * @param token0 One of the two tokens in the desired pool
     * @param token1 The other of the two tokens in the desired pool
     * @param fee The desired fee for the pool ; divide this value by 10000 to get the percent value
     * @return The pool address if it exists
     */
    @External(readonly = true)
    public Address getPool (
        Address token0, 
        Address token1, 
        int fee
    ) {
        return this.getPool.at(token0).at(token1).get(fee);
    }

    // --- Implement IConcentratedLiquidityPoolDeployer ---
    @External(readonly = true)
    public Parameters parameters() {
        return this.poolDeployer.parameters();
    }
}
