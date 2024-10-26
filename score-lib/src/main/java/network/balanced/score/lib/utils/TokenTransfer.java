package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.BalancedAddressManager.getDaofund;


public class TokenTransfer {

    public static void transfer(Address token, String to, BigInteger amount){
       NetworkAddress toNetworkAddress = NetworkAddress.parse(to);
       String NATIVE_NID = (String) Context.call(BalancedAddressManager.getXCall(), "getNetworkId");
       if(!NATIVE_NID.equals(toNetworkAddress.net())) {
           Context.require(canWithdraw(toNetworkAddress.net()), "enable can withdraw first");
           String nativeAddress = (String) Context.call(BalancedAddressManager.getAssetManager(), "getNativeAssetAddress", token, toNetworkAddress.net());
           BigInteger xCallFee = (BigInteger) Context.call(BalancedAddressManager.getDaofund(), "claimXCallFee", toNetworkAddress.net(), false);
           if (nativeAddress == null) {
               //todo: uncomment latter
               //Context.call(xCallFee, token, "crossTransfer", to, amount, new byte[0]);
           } else {
               Context.call(xCallFee, BalancedAddressManager.getAssetManager(), "withdrawTo", token, to, amount);
           }
       }else{
           Context.call(token, "transfer", Address.fromString(toNetworkAddress.account()), amount, new byte[0]);
       }
    }

    private static boolean canWithdraw(String net) {
        return (Boolean) Context.call(getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }
}
