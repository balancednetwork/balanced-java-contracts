package network.balanced.score.core.dex;


import network.balanced.score.lib.interfaces.Dex;
import network.balanced.score.lib.utils.*;
import score.Address;
import score.Context;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

import foundation.icon.xcall.NetworkAddress;

import static network.balanced.score.core.dex.DexDBVariables.*;
import static network.balanced.score.core.dex.DexDBVariables.poolLpTotal;
import static network.balanced.score.core.dex.utils.Check.isDexOn;
import static network.balanced.score.core.dex.utils.Const.SICXICX_POOL_ID;
import static network.balanced.score.core.dex.utils.Const.TAG;
import static network.balanced.score.lib.utils.Check.checkStatus;

public abstract class IRC31StandardSpokeLpToken extends FloorLimited implements Dex {

    public static String NATIVE_NID;

    @External(readonly = true)
    public BigInteger balanceOf(Address _owner, BigInteger _id) {
        if (_id.intValue() == SICXICX_POOL_ID) {
            return getICXBalance(_owner);
        } else {
            NetworkAddress owner = new NetworkAddress(NATIVE_NID, _owner);
            return DexDBVariables.balance.at(_id.intValue()).getOrDefault(owner, BigInteger.ZERO);
        }
    }

    @External(readonly = true)
    public BigInteger xBalanceOf(String _owner, BigInteger _id) {
        NetworkAddress address = NetworkAddress.valueOf(_owner);
        return DexDBVariables.balance.at(_id.intValue()).getOrDefault(address, BigInteger.ZERO);
    }

    @External
    public void transfer(Address _to, BigInteger _value, BigInteger _id, @Optional byte[] _data) {
        isDexOn();
        checkStatus();
        if (_data == null) {
            _data = new byte[0];
        }
        NetworkAddress from = new NetworkAddress(NATIVE_NID, Context.getCaller());
        NetworkAddress to = new NetworkAddress(NATIVE_NID, _to);
        _transfer(from, to, _value, _id.intValue(), _data);
    }

    @External
    public void hubTransfer(String _to, BigInteger _value, BigInteger _id, @Optional byte[] _data) {
        isDexOn();
        checkStatus();
        if (_data == null) {
            _data = new byte[0];
        }
        _transfer(
                new NetworkAddress(NATIVE_NID, Context.getCaller()),
                NetworkAddress.valueOf(_to, NATIVE_NID),
                _value,
                _id.intValue(),
                _data);
    }

    public void xHubTransfer(String from, String _to, BigInteger _value, BigInteger _id, byte[] _data) {
        _transfer(
                NetworkAddress.valueOf(from),
                NetworkAddress.valueOf(_to),
                _value,
                _id.intValue(),
                _data);
    }

    @External(readonly = true)
    public BigInteger getICXBalance(Address _address) {
        BigInteger orderId = icxQueueOrderId.get(_address);
        if (orderId == null) {
            return BigInteger.ZERO;
        }
        return icxQueue.getNode(orderId).getSize();
    }

    @External(readonly = true)
    public BigInteger totalSupply(BigInteger _id) {
        if (_id.intValue() == SICXICX_POOL_ID) {
            return icxQueueTotal.getOrDefault(BigInteger.ZERO);
        }

        return poolLpTotal.getOrDefault(_id.intValue(), BigInteger.ZERO);
    }

    void _transfer(NetworkAddress _from, NetworkAddress _to, BigInteger _value, Integer _id, byte[] _data) {

        Context.require(!isLockingPool(_id), TAG + ": Nontransferable token id");
        Context.require(_value.compareTo(BigInteger.ZERO) >= 0,
                TAG + ": Transferring value cannot be less than 0.");

        NetworkAddressDictDB<BigInteger> poolLpBalanceOfUser = balance.at(_id);
        BigInteger fromBalance = poolLpBalanceOfUser.getOrDefault(_from, BigInteger.ZERO);

        Context.require(fromBalance.compareTo(_value) >= 0, TAG + ": Out of balance");

        poolLpBalanceOfUser.set(_from, poolLpBalanceOfUser.get(_from).subtract(_value));
        poolLpBalanceOfUser.set(_to, poolLpBalanceOfUser.getOrDefault(_to, BigInteger.ZERO).add(_value));

        byte[] dataBytes = (_data == null) ? "None".getBytes() : _data;

        HubTransferSingle(BigInteger.valueOf(_id), _from.toString(), _to.toString(), _value, dataBytes);
        if (!_to.net().equals(NATIVE_NID)) {
            return;
        }

        Address contractAddress = Address.fromString(_to.account());
        if (!contractAddress.isContract()) {
            return;
        }

        if(isNative(_from)){
            Context.call(contractAddress, "onIRC31Received", Address.fromString(_from.account()), Address.fromString(_from.account()), _id, _value, dataBytes);
        }else {
            Context.call(contractAddress, "onXIRC31Received", _from.toString(), _from.toString(), _id, _value, dataBytes);
        }
    }

    protected boolean isNative(NetworkAddress address) {
        return address.net().equals(NATIVE_NID);
    }

    boolean isLockingPool(Integer id) {
        return id.equals(SICXICX_POOL_ID);
    }

    @EventLog(indexed = 3)
    public void TransferSingle(Address _operator, Address _from, Address _to, BigInteger _id, BigInteger _value) {
    }

    @EventLog(indexed = 3)
    public void HubTransferSingle(BigInteger _id, String _from, String _to, BigInteger _value, byte[] _data) {
    }

}
