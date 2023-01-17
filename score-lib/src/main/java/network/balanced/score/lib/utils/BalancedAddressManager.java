/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.lib.utils;

import score.Address;
import score.Context;
import score.DictDB;

import static network.balanced.score.lib.utils.Check.readonly;

public class BalancedAddressManager {
    private static final String TAG = "BalancedAddressManager";
    private static final Address mainnetGovernance = Address.fromString("cx44250a12074799e26fdeee75648ae47e2cc84219");
    public static final DictDB<String, Address> contractAddresses = Context.newDictDB(TAG + "ContractAddresses",
            Address.class);

    private static Address baln;
    private static Address bnusd;
    private static Address bwt;
    private static Address daofund;
    private static Address staking;
    private static Address stakedLp;
    private static Address stabilityFund;
    private static Address sicx;
    private static Address rewards;
    private static Address reserve;
    private static Address rebalance;
    private static Address oracle;
    private static Address balancedOracle;
    private static Address loans;
    private static Address feehandler;
    private static Address dividends;
    private static Address dex;
    private static Address boostedBaln;

    public static void setGovernance(Address address) {
        contractAddresses.set(Names.GOVERNANCE, address);
    }

    public static Address getAddressByName(String name) {
        return contractAddresses.get(name);
    }

    public static void resetAddress(String name) {
        Address address = fetchAddress(name);
        contractAddresses.set(name, address);
    }

    public static Address fetchAddress(String name) {
        return Context.call(Address.class, contractAddresses.getOrDefault(Names.GOVERNANCE, mainnetGovernance),
                "getAddress", name);
    }

    private static Address getAddress(String name) {
        Address address = contractAddresses.get(name);
        if (address == null) {
            address = fetchAddress(name);
            try {
                contractAddresses.set(name, address);
            } catch (Exception ignored) {
            }
        }

        return address;
    }

    public static Address getBaln() {
        if (baln != null) {
            return baln;
        } else if (readonly()) {
            return getAddress(Names.BALN);
        }
        baln = getAddress(Names.BALN);

        return baln;
    }

    public static Address getBnusd() {
        if (bnusd != null) {
            return bnusd;
        } else if (readonly()) {
            return getAddress(Names.BNUSD);
        }
        bnusd = getAddress(Names.BNUSD);

        return bnusd;
    }

    public static Address getBwt() {
        if (bwt != null) {
            return bwt;
        } else if (readonly()) {
            return getAddress(Names.WORKERTOKEN);
        }
        bwt = getAddress(Names.WORKERTOKEN);

        return bwt;
    }

    public static Address getDaofund() {
        if (daofund != null) {
            return daofund;
        } else if (readonly()) {
            return getAddress(Names.DAOFUND);
        }
        daofund = getAddress(Names.DAOFUND);

        return daofund;
    }

    public static Address getStaking() {
        if (staking != null) {
            return staking;
        } else if (readonly()) {
            return getAddress(Names.STAKING);
        }
        staking = getAddress(Names.STAKING);

        return staking;
    }

    public static Address getStakedLp() {
        if (stakedLp != null) {
            return stakedLp;
        } else if (readonly()) {
            return getAddress(Names.STAKEDLP);
        }
        stakedLp = getAddress(Names.STAKEDLP);

        return stakedLp;
    }

    public static Address getStabilityFund() {
        if (stabilityFund != null) {
            return stabilityFund;
        } else if (readonly()) {
            return getAddress(Names.STABILITY);
        }
        stabilityFund = getAddress(Names.STABILITY);

        return stabilityFund;
    }

    public static Address getSicx() {
        if (sicx != null) {
            return sicx;
        } else if (readonly()) {
            return getAddress(Names.SICX);
        }
        sicx = getAddress(Names.SICX);

        return sicx;
    }

    public static Address getRewards() {
        if (rewards != null) {
            return rewards;
        } else if (readonly()) {
            return getAddress(Names.REWARDS);
        }
        rewards = getAddress(Names.REWARDS);

        return rewards;
    }

    public static Address getReserve() {
        if (reserve != null) {
            return reserve;
        } else if (readonly()) {
            return getAddress(Names.RESERVE);
        }
        reserve = getAddress(Names.RESERVE);

        return reserve;
    }

    public static Address getRebalance() {
        if (rebalance != null) {
            return rebalance;
        } else if (readonly()) {
            return getAddress(Names.REBALANCING);
        }
        rebalance = getAddress(Names.REBALANCING);

        return rebalance;
    }

    public static Address getOracle() {
        if (oracle != null) {
            return oracle;
        } else if (readonly()) {
            return getAddress(Names.ORACLE);
        }
        oracle = getAddress(Names.ORACLE);

        return oracle;
    }

    public static Address getBalancedOracle() {
        if (balancedOracle != null) {
            return balancedOracle;
        } else if (readonly()) {
            return getAddress(Names.BALANCEDORACLE);
        }
        balancedOracle = getAddress(Names.BALANCEDORACLE);

        return balancedOracle;
    }

    public static Address getLoans() {
        if (loans != null) {
            return loans;
        } else if (readonly()) {
            return getAddress(Names.LOANS);
        }
        loans = getAddress(Names.LOANS);

        return loans;
    }

    public static Address getFeehandler() {
        if (feehandler != null) {
            return feehandler;
        } else if (readonly()) {
            return getAddress(Names.FEEHANDLER);
        }
        feehandler = getAddress(Names.FEEHANDLER);

        return feehandler;
    }

    public static Address getDividends() {
        if (dividends != null) {
            return dividends;
        } else if (readonly()) {
            return getAddress(Names.DIVIDENDS);
        }
        dividends = getAddress(Names.DIVIDENDS);

        return dividends;
    }

    public static Address getDex() {
        if (dex != null) {
            return dex;
        } else if (readonly()) {
            return getAddress(Names.DEX);
        }
        dex = getAddress(Names.DEX);

        return dex;
    }

    public static Address getBoostedBaln() {
        if (boostedBaln != null) {
            return boostedBaln;
        } else if (readonly()) {
            return getAddress(Names.BOOSTED_BALN);
        }
        boostedBaln = getAddress(Names.BOOSTED_BALN);

        return boostedBaln;
    }

    public static Address getGovernance() {
        return contractAddresses.get(Names.GOVERNANCE);
    }
}