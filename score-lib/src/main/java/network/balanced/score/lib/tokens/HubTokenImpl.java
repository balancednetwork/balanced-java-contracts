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

package network.balanced.score.lib.tokens;

import icon.xcall.lib.messages.HubTokenMessages;
import network.balanced.score.lib.interfaces.tokens.HubToken;
import network.balanced.score.lib.interfaces.tokens.HubTokenXCall;
import network.balanced.score.lib.utils.BalancedAddressManager;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import xcall.score.lib.util.NetworkAddress;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.Check.only;
import static network.balanced.score.lib.utils.Check.onlyOwner;

public class HubTokenImpl extends SpokeTokenImpl implements HubToken {
    private final static String CROSS_CHAIN_SUPPLY = "cross_chain_supply";
    private final static String CONNECTED_CHAINS = "connected_chains";
    private final static String SPOKE_CONTRACTS = "spoke_contract";
    private final static String SPOKE_LIMITS = "spoke_limits";

    static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);

    protected final DictDB<String, BigInteger> crossChainSupply = Context.newDictDB(CROSS_CHAIN_SUPPLY, BigInteger.class);
    protected final ArrayDB<NetworkAddress> connectedChains = Context.newArrayDB(CONNECTED_CHAINS, NetworkAddress.class);
    // net -> address
    protected final DictDB<String, NetworkAddress> spokeContracts = Context.newDictDB(SPOKE_CONTRACTS, NetworkAddress.class);
    // net -> int
    protected final DictDB<String, BigInteger> spokeLimits = Context.newDictDB(SPOKE_LIMITS, BigInteger.class);

    public HubTokenImpl(String _nid, String _tokenName, String _symbolName, @Optional BigInteger _decimals) {
        super(_nid, _tokenName, _symbolName, _decimals);
    }

    @EventLog(indexed = 1)
    public void XTransfer(BigInteger id, String _from, String _to, BigInteger _value, byte[] _data) {

    }

    @External(readonly = true)
    public String[] getConnectedChains() {
        int numberOfChains = connectedChains.size();
        String[] chains = new String[numberOfChains];
        for (int i = 0; i < numberOfChains; i++) {
            chains[i] = connectedChains.get(i).toString();
        }

        return chains;
    }

    @External
    public void addChain(String _networkAddress, BigInteger limit) {
        onlyOwner();
        NetworkAddress networkAddress = NetworkAddress.parse(_networkAddress);
        connectedChains.add(networkAddress);
        spokeContracts.set(networkAddress.net(), networkAddress);
        spokeLimits.set(networkAddress.net(), limit);
    }

    @External
    public void setSpokeLimit(String networkId, BigInteger limit) {
        onlyOwner();
        spokeLimits.set(networkId, limit);
    }

    @External(readonly = true)
    public BigInteger xSupply(String spokeAddress) {
        return crossChainSupply.getOrDefault(spokeAddress, BigInteger.ZERO);
    }

    @External(readonly = true)
    public BigInteger xTotalSupply() {
        BigInteger supply = totalSupply();
        int numberOfChains = connectedChains.size();
        for (int i = 0; i < numberOfChains; i++) {
            NetworkAddress spokeAddress = connectedChains.get(i);
            BigInteger supplyAt = crossChainSupply.getOrDefault(spokeAddress.toString(), BigInteger.ZERO);
            supply = supply.add(supplyAt);
        }

        return supply;
    }

    @External
    @Payable
    public void crossTransfer(String _to, BigInteger _value, @Optional byte[] _data) {
        NetworkAddress from = new NetworkAddress(NATIVE_NID, Context.getCaller());
        NetworkAddress to = NetworkAddress.valueOf(_to, NATIVE_NID);
        if (isNative(to)) {
            _transfer(from, to, _value, _data);
            return;
        }

        _transferToSpoke(Context.getValue(), from, to, _value, _data);
    }

    public void xCrossTransferRevert(String from, String _to, BigInteger _value) {
        Context.require(from.equals(new NetworkAddress(NATIVE_NID, BalancedAddressManager.getXCall()).toString()));
        NetworkAddress to = NetworkAddress.valueOf(_to);
        NetworkAddress spokeContract = spokeContracts.get(to.net());
        _transferToICON(spokeContract, to, _value);
    }

    public void xCrossTransfer(String from, String _from, String _to, BigInteger _value, byte[] _data) {
        NetworkAddress spokeContract = NetworkAddress.valueOf(from);
        Context.require(spokeContracts.get(spokeContract.net()).equals(spokeContract), from + " is not a connected contract");
        if (_to.isEmpty() || _to.equals(_from)) {
            _transferToICON(spokeContract, NetworkAddress.valueOf(_from), _value);
            XTransfer(BigInteger.ZERO, _from, _from, _value, _data);
            return;
        }

        NetworkAddress to = NetworkAddress.valueOf(_to);
        _transferToICON(spokeContract, to, _value);

        if (!isNative(to)) {
            _transferToSpoke(BigInteger.ZERO, to, to, _value, _data);
            return;
        }

        Address address = Address.fromString(to.account());
        XTransfer(BigInteger.ZERO, _from, _to, _value, _data);
        if (address.isContract()) {
            Context.call(address, "xTokenFallback", _from, _value, _data);
        }

    }

    public void xWithdraw(String from, BigInteger _value) {
        NetworkAddress caller = NetworkAddress.valueOf(from);
        _transferToSpoke(BigInteger.ZERO, caller, caller, _value, new byte[0]);
    }

    public void _transferToICON(NetworkAddress spokeContract, NetworkAddress to, BigInteger value) {
        BigInteger prevSourceSupply = crossChainSupply.getOrDefault(spokeContract.toString(), BigInteger.ZERO);
        BigInteger newSupply = prevSourceSupply.subtract(value);
        Context.require(newSupply.compareTo(BigInteger.ZERO) >= 0);
        crossChainSupply.set(spokeContract.toString(), newSupply);
        _mint(to, value);
    }

    public void _transferToSpoke(BigInteger fee, NetworkAddress from, NetworkAddress to, BigInteger value, byte[] data) {
        _burn(from, value);
        NetworkAddress spokeAddress = spokeContracts.get(to.net());
        Context.require(spokeAddress != null, to.net() + " is not yet connected");
        BigInteger prevSupply = crossChainSupply.getOrDefault(spokeAddress.toString(), BigInteger.ZERO);
        BigInteger newSupply = prevSupply.add(value);
        Context.require(newSupply.compareTo(spokeLimits.getOrDefault(to.net(), BigInteger.ZERO)) < 0, "This chain is not allowed to mint more tokens");
        crossChainSupply.set(spokeAddress.toString(), newSupply);

        data = (data == null) ? new byte[0] : data;
        byte[] rollback = HubTokenMessages.xCrossTransferRevert(to.toString(), value);
        byte[] callData = HubTokenMessages.xCrossTransfer(from.toString(), to.toString(), value, data);

        Context.call(fee, BalancedAddressManager.getXCall(), "sendCallMessage", spokeAddress.toString(), callData, rollback);
        XTransfer(BigInteger.ZERO, from.toString(), to.toString(), value, data);
    }

    @Override
    @External
    public void handleCallMessage(String _from, byte[] _data) {
        only(BalancedAddressManager.getXCall());
        HubTokenXCall.process(this, _from, _data);
    }

}
