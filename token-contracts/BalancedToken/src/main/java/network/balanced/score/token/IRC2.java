package network.balanced.score.token;

import static network.balanced.score.token.Constants.*;

import java.math.BigInteger;

import network.balanced.score.token.util.Mathematics;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

/**
Implementation of IRC2
**/
public abstract class IRC2 extends BaseScore implements com.iconloop.score.token.irc2.IRC2{

	private static final String TAG = "IRC_2";
	private static final Address EOA_ZERO = Address.fromString("hx0000000000000000000000000000000000000000");

	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DECIMALS = "decimals";
	private static final String TOTAL_SUPPLY = "total_supply";
	private static final String BALANCES = "balances";
	private static final String ADMIN = "admin";

	private VarDB<String> name = Context.newVarDB(NAME, String.class);
	private VarDB<String> symbol = Context.newVarDB(SYMBOL, String.class);
	protected VarDB<BigInteger> decimals = Context.newVarDB(DECIMALS, BigInteger.class);
	private VarDB<BigInteger> totalSupply = Context.newVarDB(TOTAL_SUPPLY, BigInteger.class);
	protected DictDB<Address, BigInteger> balances = Context.newDictDB(BALANCES, BigInteger.class);
	private VarDB<Address> admin = Context.newVarDB(ADMIN, Address.class);

	/**
	Variable Initialization.

	:param _tokenName: The name of the token.
	:param _symbolName: The symbol of the token.
	:param _initialSupply: The total number of tokens to initialize with.
			It is set to total supply in the beginning, 0 by default.
	:param _decimals: The number of decimals. Set to 18 by default.

	total_supply is set to `_initialSupply`* 10 ^ decimals.

	Raise
	InvalidNameError
		If the length of strings `_symbolName` and `_tokenName` is 0 or less.
	ZeroValueError
		If `_initialSupply` is 0 or less.
		If `_decimals` value is 0 or less.
	**/
	public IRC2(String _tokenName,
			   String _symbolName,
			   @Optional BigInteger _initialSupply,
			   @Optional BigInteger _decimals,
			   @Optional boolean update) {
		if(update) {
			onUpdate();
			return;
		}
		if(_initialSupply == null) {
			_initialSupply = Constants.DEFAULT_INITIAL_SUPPLY;
		}

		if(_decimals == null) {
			_decimals = Constants.DEFAULT_DECIMAL_VALUE;
		}

		if (_symbolName.length() <= 0) {
			//TODO: verify if we throw an exception, the transaction is rollbacked
			Context.revert("InvalidNameError(Invalid Symbol name)");
		}

		if (_tokenName.length() <= 0) {
			Context.revert("InvalidNameError(Invalid Token Name)");
		}

		if (_initialSupply.compareTo(ZERO) < 0) {
			Context.revert("ZeroValueError(Initial Supply cannot be less than zero)");
		}
		if (_decimals.compareTo(ZERO) < 0) {
			Context.revert("ZeroValueError(Decimals cannot be less than zero)");
		}

		BigInteger totalSupply = Mathematics.pow(_initialSupply.multiply(TEN), _decimals.intValue());

		Context.println(TAG + " | on_install: total_supply=" + totalSupply);

		this.name.set(_tokenName);
		this.symbol.set(_symbolName);
		this.totalSupply.set(totalSupply);
		this.decimals.set(_decimals);
		this.balances.set( Context.getCaller(), totalSupply);
	}

	public void onUpdate() {
		Context.println(TAG + " | on update");
	}

	/**
	Returns the name of the token.
	**/
	@Override
	public String name() {
		return this.name.get();
	}

	/**
	Returns the symbol of the token.
	**/
	@Override
	public String symbol() {
		return this.symbol.get();
	}

	/**
	Returns the number of decimals.
	**/
	@Override
	public BigInteger decimals() {
		return this.decimals.get();
	}

	/**
	Returns the total number of tokens in existence.
	**/
	@Override
	public BigInteger totalSupply() {
		return this.totalSupply.get();
	}

	/**
	Returns the amount of tokens owned by the account.

	:param _owner: The account whose balance is to be checked.
	:return Amount of tokens owned by the `account` with the given address.
	**/
	@Override
	public BigInteger balanceOf(Address _owner) {
		return this.balances.get(_owner);
	}

	/**
	Sets the authorized address.

	:param _admin: The authorized admin address.
	**/
	@External
	public void setAdmin(Address _admin) {
		onlyOwner();
		this.admin.set(_admin);
	}

	/**
	Returns the authorized admin address.
	**/
	@External(readonly=true)
	public Address getAdmin() {
		return this.admin.get();
	}

	@EventLog(indexed = 3)
	@Override
	public void Transfer(Address _from, Address _to, BigInteger _value, byte[] _data) {}

	@EventLog(indexed = 1)
	public void Mint(Address account, BigInteger amount, byte[] _data) {}

	@EventLog(indexed = 1)
	public void Burn(Address account, BigInteger amount) {}

	@External
	@Override
	public void transfer(Address _to, BigInteger _value, @Optional byte[] _data) {
		if(_data == null) {
			_data = "None".getBytes();
		}

		_transfer(Context.getCaller(), _to, _value, _data);
	}

	/**
	Transfers certain amount of tokens from sender to the recepient.
	This is an internal function.

	:param _from: The account from which the token is to be transferred.
	:param _to: The account to which the token is to be transferred.
	:param _value: The no. of tokens to be transferred.
	:param _data: Any information or message

	Raises
	ZeroValueError
		if the value to be transferrd is less than 0
	InsufficientBalanceError
		if the sender has less balance than the value to be transferred
	**/
	public void _transfer(Address from , Address to, BigInteger value, byte[] data) {
		if (value == null || value.compareTo(ZERO) < 0 ) {
			Context.revert("ZeroValueError(Transferring value cannot be less than 0.)");
		}

		BigInteger bFrom = this.balances.getOrDefault(from, ZERO);

		if (bFrom.compareTo(value) < 0){
			Context.revert("InsufficientBalanceError(Insufficient balance.)");
		}

		this.balances.set(from, bFrom.subtract(value) );
		this.balances.set(to, this.balances.getOrDefault(to, ZERO).add(value));

		// Emits an event log `Transfer`
		Transfer(from, to, value, data);

		if (to.isContract()) {
			/*
			If the recipient is SCORE,
			then calls `tokenFallback` to hand over control.
			*/
			Context.call(to, "tokenFallback", from, value, data);
		}
	}

	/**
	Creates amount number of tokens, and assigns to account
	Increases the balance of that account and total supply.
	This is an internal function

	:param account: The account at whhich token is to be created.
	:param amount: Number of tokens to be created at the `account`.
	:param _data: Any information or message

	Raises
	ZeroValueError
		if the `amount` is less than or equal to zero.
	**/
	public void _mint(Address account, BigInteger amount, byte[] _data) {
		onlyAdmin();

		if (amount != null && amount.compareTo(ZERO) <= 0) {
			Context.revert("ZeroValueError(Invalid Value)");
		}

		this.totalSupply.set(this.totalSupply.getOrDefault(ZERO).add(amount) );
		this.balances.set(account, this.balances.getOrDefault(account, ZERO).add(amount) );

		// Emits an event log Mint
		Mint(account, amount, _data);

		// Emits an event log `Transfer`
		Transfer(EOA_ZERO, account, amount, _data);

		if (account.isContract()) {
			/*
			If the recipient is SCORE,
			then calls `tokenFallback` to hand over control.
			*/
			Context.call(account, "tokenFallback", EOA_ZERO, amount, _data);
		}
	}

	/**
	Destroys `amount` number of tokens from `account`
	Decreases the balance of that `account` and total supply.
	This is an internal function

	:param account: The account at which token is to be destroyed.
	:param amount: The `amount` of tokens of `account` to be destroyed.

	Raises
	ZeroValueError
		if the `amount` is less than or equal to zero
	**/
	public void _burn(Address account, BigInteger amount) {
		onlyAdmin();

		BigInteger balance = this.balanceOf(account);
		   
		if  ( amount == null || amount.compareTo(ZERO) <= 0  || amount.compareTo(balance) >= 0) {
			Context.revert("ZeroValueError(Invalid Value)");
		}

		this.totalSupply.set( this.totalSupply.getOrDefault(ZERO).subtract(amount));
		this.balances.set(account, balance.subtract(amount));

		// Emits an event log Burn
		Burn(account, amount);

		// Emits an event log `Transfer`
		Transfer(account, EOA_ZERO, amount, "None".getBytes());
	}
}
