package network.balanced.score.core;

import score.Context;
import score.VarDB;
import score.DictDB;
import score.Address;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;

import java.util.Map;

import score.Context;
import java.util.ArrayList;

import java.math.BigInteger;


public class ReserveMock {
    private final Address token;
    
    public ReserveMock(Address token) {
        this.token = token;
    }

    @External
    public void redeem(Address to, BigInteger amount, BigInteger icxRate) {
        Context.call(token, "transfer", Context.getCaller(), amount, new byte[0]);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
    }
   
}
