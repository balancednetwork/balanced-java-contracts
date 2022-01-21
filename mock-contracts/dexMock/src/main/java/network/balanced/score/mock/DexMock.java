package network.balanced.score.mock;

import score.Context;
import score.VarDB;
import score.Address;
import score.annotation.External;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import java.math.BigInteger;

public class DexMock {
    public static final BigInteger EXA =  BigInteger.valueOf(1000000).multiply(BigInteger.valueOf(1000000)).multiply(BigInteger.valueOf(1000000));

    public static final VarDB<Address> sicx = Context.newVarDB("sicx", Address.class);
    public static final VarDB<Address> bnusd = Context.newVarDB("bnusd", Address.class);

    public DexMock(Address _sicx, Address _bnusd) {
        sicx.set(_sicx);
        bnusd.set(_bnusd);
    }

    @External
    public BigInteger getSicxBnusdPrice() {
        BigInteger sicxLiquidity = (BigInteger) Context.call(sicx.get(), "balanceOf", Context.getAddress());
        BigInteger bnusdLiquidity  = (BigInteger) Context.call(bnusd.get(), "balanceOf", Context.getAddress());
      
        return sicxLiquidity.multiply(EXA).divide(bnusdLiquidity);
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address token = Context.getCaller();

        Context.require(_value.signum() > 0, "Token Fallback: Token value should be a positive number");
        String unpackedData = new String(_data);
        if (unpackedData.equals("") || unpackedData.equals("None")) {
            return;
        }

        JsonObject json = Json.parse(unpackedData).asObject();

        String method = json.get("method").asString();
        JsonObject params = json.get("params").asObject();

        if (method.equals("_swap")) {
            BigInteger sicxLiquidity = (BigInteger) Context.call(sicx.get(), "balanceOf", Context.getAddress());
            BigInteger bnusdLiquidity  = (BigInteger) Context.call(bnusd.get(), "balanceOf", Context.getAddress());
            if (token == bnusd.get()) {
                BigInteger tokensReceived = sicxLiquidity.multiply(_value).divide(bnusdLiquidity);
                Context.call(sicx.get(), "transfer", _from, tokensReceived, new byte[0]);
            } else if (token == sicx.get()){
                BigInteger tokensReceived = bnusdLiquidity.multiply(_value).divide(sicxLiquidity);
                Context.call(bnusd.get(), "transfer", _from, tokensReceived, new byte[0]);
            }
        }
        
    }

}