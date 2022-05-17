package network.balanced.test.util;

import network.balanced.test.TransactionHandler;
import foundation.icon.icx.KeyWallet;
import java.math.BigInteger;

public class Helpers {
    public static KeyWallet[] setupWallets(TransactionHandler txHandler, int numberOfWallets, BigInteger initalBalance) throws Exception {
        KeyWallet[] wallets = new KeyWallet[numberOfWallets];
        for (int i = 0; i < wallets.length; i++) {
            wallets[i] = KeyWallet.create();
            txHandler.transfer(wallets[i].getAddress(), initalBalance);
        }

        return wallets;
    }
}