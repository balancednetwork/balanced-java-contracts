package network.balanced.score.core;

import com.eclipsesource.json.*;
import score.*;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.Checks.onlyGovernance;
import static network.balanced.score.core.Checks.onlyOwner;


public class FeeHandler {
    public static final String TAG = "FeeHandler";
    public static final ArrayDB<Address> accepted_dividends_tokens = Context.newArrayDB("dividend_tokens", Address.class);
    public static final DictDB<Address, BigInteger> last_fee_processing_block = Context.newDictDB("last_block", BigInteger.class);
    public static final VarDB<BigInteger> fee_processing_interval = Context.newVarDB("block_interval", BigInteger.class);
    public static final VarDB<byte[]> last_txhash = Context.newVarDB("last_txhash", byte[].class);
    public static final BranchDB<Address, DictDB<Address, String>> routes = Context.newBranchDB("routes", String.class);
    public static final VarDB<Address> governance = Context.newVarDB("governance", Address.class);
    public static final VarDB<Boolean> enabled = Context.newVarDB("enabled", Boolean.class);
    public static final ArrayDB<Address> allowed_address = Context.newArrayDB("allowed_address", Address.class);
    public static final VarDB<BigInteger> next_allowed_addresses_index = Context.newVarDB("_next_allowed_addresses_index", BigInteger.class);

    public FeeHandler(@Optional Address governance) {
        if (governance != null) {
            Context.require(governance.isContract(), "FeeHandler: Governance address should be a contract");
            FeeHandler.governance.set(governance);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced " + TAG;
    }

    @External
    public void enable() {
        onlyGovernance();
        enabled.set(Boolean.TRUE);
    }

    @External
    public void disable() {
        onlyGovernance();
        enabled.set(Boolean.FALSE);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        onlyGovernance();
        Context.require(_tokens.length <= 10, "There can be a maximum of 10 accepted dividend tokens.");
        for (Address address : _tokens) {
            Context.require(address.isContract(), TAG + " :Address provided is an EOA address. Only contract addresses are allowed.");
        }

        while (accepted_dividends_tokens.size() != 0) {
            accepted_dividends_tokens.pop();
        }

        for (Address token : _tokens) {
            accepted_dividends_tokens.add(token);
        }
    }

    @External(readonly = true)
    public List<Address> getAcceptedDividendTokens() {
        List<Address> tokens = new ArrayList<>();
        for (int i = 0; i < accepted_dividends_tokens.size(); i++) {
            tokens.add(accepted_dividends_tokens.get(i));
        }
        return tokens;
    }

    @External
    public void setRoute(Address _fromToken, Address _toToken, Address[] _path) {
        onlyGovernance();
        JsonArray path = new JsonArray();
        for (Address address : _path) {
            Context.require(address.isContract(), TAG + " :Address provided is an EOA address. Only contract addresses are allowed.");
            path.add(address.toString());
        }
        routes.at(_fromToken).set(_toToken, path.toString());
    }

    @External
    public void deleteRoute(Address _fromToken, Address _toToken) {
        onlyGovernance();
        routes.at(_fromToken).set(_toToken, null);
    }

    @External(readonly = true)
    public Map<String, Object> getRoute(Address _fromToken, Address _toToken) {
        String path = routes.at(_fromToken).getOrDefault(_toToken, "");
        if (path.equals("")) {
            return Map.of();
        }
        JsonArray pathJson = Json.parse(path).asArray();
        String[] pa = new String[pathJson.size()];
        for (int i = 0; i < pa.length; i++) {
            pa[i] = pathJson.get(i).asString();
        }
        return Map.of("fromToken", _fromToken,
                "toToken", _toToken,
                "path", pa);
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        onlyGovernance();
        fee_processing_interval.set(_interval);
    }

    @External(readonly = true)
    public BigInteger getFeeProcessingInterval() {
        return fee_processing_interval.get();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address sender = Context.getCaller();
        if (last_txhash.getOrDefault(new byte[0]) == (Context.getTransactionHash())) {
            return;
        }
        if (!timeForFeeProcessing(sender)) {
            return;
        } else {
            last_txhash.set(Context.getTransactionHash());
        }
        for (int i = 0; i < accepted_dividends_tokens.size(); i++) {
            if (accepted_dividends_tokens.get(i) == sender) {
                transferToken(sender, getContractAddress("dividends"), getTokenBalance(sender), new byte[0]);
            }
        }
        last_fee_processing_block.set(sender, BigInteger.valueOf(Context.getBlockHeight()));
    }

    @External
    public void add_allowed_address(Address address) {
        onlyOwner();
        Context.require(address.isContract(), TAG + " :Address provided is an EOA address. A contract address is required.");
        allowed_address.add(address);
    }

    @External(readonly = true)
    public List<Address> get_allowed_address(int offset) {
        int start = offset;
        int end = Math.min(allowed_address.size(), offset + 20) - 1;
        List<Address> address_list = new ArrayList<>();
        for (int i = start; i < end + 1; i++) {
            address_list.add(allowed_address.get(i));
        }
        return address_list;
    }

    @External
    public void route_contract_balances() {
        BigInteger starting_index = next_allowed_addresses_index.getOrDefault(BigInteger.ZERO);
        BigInteger current_index = starting_index;
        boolean loop_flag = false;
        Address address;
        BigInteger balance;

        BigInteger allowed_address_length = BigInteger.valueOf(allowed_address.size());
        Context.require(allowed_address_length.signum() > 0, TAG + ": No allowed addresses.");
        while (true) {
            if (current_index.compareTo(allowed_address_length) > -1) {
                current_index = BigInteger.ZERO;
            }
            address = allowed_address.get(current_index.intValue());
            balance = getTokenBalance(address);
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                break;
            } else {
                if (loop_flag && (starting_index.equals(current_index))) {
                    Context.revert("No fees on the contract.");
                }
                current_index = current_index.add(BigInteger.ONE);

                if (!loop_flag) {
                    loop_flag = true;
                }
            }

        }
        next_allowed_addresses_index.set(current_index.add(BigInteger.ONE));
        JsonArray path;
        try {
            String route = routes.at(address).getOrDefault(getContractAddress("baln"), "");
            path = Json.parse(route).asArray();
        } catch (Exception e) {
            path = new JsonArray();
        }

        try {
            if (path.size() > 0) {
                transferToken(address, getContractAddress("router"), balance, createDataFieldRouter(
                        getContractAddress("dividends"), path));
            } else {
                transferToken(address, getContractAddress("dex"), balance, createDataFieldDex(
                        getContractAddress("baln"), getContractAddress("dividends")));
            }
        } catch (Exception e) {
            Context.revert("No fees on the contract " + address + " failed" + e.getMessage());
        }
    }

    public byte[] createDataFieldRouter(Address _receiver, JsonArray _path) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", "_swap");
        map.put("params", Map.of("path", _path, "receiver", _receiver.toString()));
        String data = map.toString();
        return data.getBytes();
    }

    public byte[] createDataFieldDex(Address _toToken, Address _receiver) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", "_swap");
        map.put("params", Map.of("toToken", _toToken.toString(), "receiver", _receiver.toString()));
        String data = map.toString();
        return data.getBytes();
    }

    public Address getContractAddress(String _contract) {
        return (Address) Context.call(governance.getOrDefault(Checks.defaultAddress), "getContractAddress", _contract);
    }

    public boolean timeForFeeProcessing(Address _token) {
        if (enabled.getOrDefault(Boolean.FALSE).equals(false)) {
            return Boolean.FALSE;
        }
        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger last_conversion = last_fee_processing_block.getOrDefault(_token, BigInteger.ZERO);
        BigInteger target_block = last_conversion.add(fee_processing_interval.getOrDefault(BigInteger.ZERO));
        if (last_conversion.signum() > 0) {
            return Boolean.TRUE;
        } else if (blockHeight.compareTo(target_block) < 0) {
            return Boolean.FALSE;
        } else {
            return Boolean.TRUE;
        }
    }

    public BigInteger getTokenBalance(Address _token) {
        return (BigInteger) Context.call(_token, "balanceOf", Context.getAddress());
    }

    public void transferToken(Address _token, Address _to, BigInteger _amount, byte[] _data) {
        Context.call(_token, "transfer", _to, _amount, _data);
    }
}
