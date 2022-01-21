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
import java.util.ArrayList;

import java.math.BigInteger;


public class GovernanceMock {
    public GovernanceMock() {
    }

    @External
    public Address getContractAddress(String name) {
        return Context.getAddress();
    }

    @External
    public void call(Address contractAddress, String method, Object... params) {
        Context.call(contractAddress, method, params);
    }
}
