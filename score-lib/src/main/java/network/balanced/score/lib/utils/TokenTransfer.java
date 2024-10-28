package network.balanced.score.lib.utils;

import foundation.icon.xcall.NetworkAddress;
import score.Address;
import score.Context;

import java.math.BigInteger;

import static network.balanced.score.lib.utils.BalancedAddressManager.getDaofund;

public class TokenTransfer {

    public static void transfer(Address token, String to, BigInteger amount, boolean toNative, byte[] data){
       NetworkAddress toNetworkAddress = NetworkAddress.parse(to);
       String NATIVE_NID = (String) Context.call(BalancedAddressManager.getXCall(), "getNetworkId");
       if(!NATIVE_NID.equals(toNetworkAddress.net())) {
           if(canWithdraw(toNetworkAddress.net())) {
               String nativeAddress = (String) Context.call(BalancedAddressManager.getAssetManager(), "getNativeAssetAddress", token, toNetworkAddress.net());
               BigInteger xCallFee = (BigInteger) Context.call(BalancedAddressManager.getDaofund(), "claimXCallFee", toNetworkAddress.net(), false);
               if (nativeAddress == null) {
                   Context.require(!toNative,  "Native swaps are not supported for this network");
                   Context.call(xCallFee, token, "crossTransfer", to, amount, data);
               } else {
                   if (toNative) {
                       Context.call(xCallFee, BalancedAddressManager.getAssetManager(), "withdrawNativeTo", token, to, amount);
                   } else {
                       Context.call(xCallFee, BalancedAddressManager.getAssetManager(), "withdrawTo", token, to, amount);
                   }
               }
           }else{
               Context.require(!toNative,  "Native swaps are not supported for this network");
               Context.call(token, "hubTransfer", toNetworkAddress.toString(), amount, data);
           }
       }else{
           Context.call(token, "transfer", Address.fromString(toNetworkAddress.account()), amount, data);
       }
    }

    public static void transfer(Address token, String to, BigInteger amount){
        transfer(token, to, amount, false, new byte[0]);
    }

    private static boolean canWithdraw(String net) {
        return (Boolean) Context.call(getDaofund(), "getXCallFeePermission", Context.getAddress(), net);
    }
}
