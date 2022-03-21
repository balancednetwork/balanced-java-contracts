from iconservice import *
from .IIRC2 import TokenStandard
from ..utils.checks import *
from ..utils.consts import *

TAG = 'IRC_2'
EOA_ZERO = Address.from_string('hx' + '0' * 40)

class InsufficientBalanceError(Exception):
	pass


class ZeroValueError(Exception):
	pass


class InvalidNameError(Exception):
	pass


class stakingManagementInterface(InterfaceScore):
	@interface
	def transferUpdateDelegations(self, _from: Address, _to: Address, _value: int):
		pass


# An interface of tokenFallback.
# Receiving SCORE that has implemented this interface can handle
# the receiving or further routine.
class TokenFallbackInterface(InterfaceScore):
	@interface
	def tokenFallback(self, _from: Address, _value: int, _data: bytes):
		pass


class IRC2(TokenStandard, IconScoreBase):
	"""
	Implementation of IRC2
	"""
	_NAME = 'name'
	_SYMBOL = 'symbol'
	_DECIMALS = 'decimals'
	_TOTAL_SUPPLY = 'total_supply'
	_BALANCES = 'balances'
	_ADMIN = 'admin'

	def __init__(self, db: IconScoreDatabase) -> None:
		super().__init__(db)

		self._name = VarDB(self._NAME, db, value_type=str)
		self._symbol = VarDB(self._SYMBOL, db, value_type=str)
		self._decimals = VarDB(self._DECIMALS, db, value_type=int)
		self._total_supply = VarDB(self._TOTAL_SUPPLY, db, value_type=int)
		self._balances = DictDB(self._BALANCES, db, value_type=int)
		self._admin = VarDB(self._ADMIN, db, value_type=Address)

	def on_install(self, _tokenName: str,
						 _symbolName: str,
						 _initialSupply: int = DEFAULT_INITIAL_SUPPLY,
						 _decimals: int = DEFAULT_DECIMAL_VALUE) -> None:
		"""
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
		"""
		if len(_symbolName) <= 0:
			raise InvalidNameError("Invalid Symbol name")
		if len(_tokenName) <= 0:
			raise InvalidNameError("Invalid Token Name")
		if _initialSupply < 0:
			raise ZeroValueError("Initial Supply cannot be less than zero")
		if _decimals < 0:
			raise ZeroValueError("Decimals cannot be less than zero")

		super().on_install()

		total_supply = _initialSupply * 10 ** _decimals

		Logger.debug(f'on_install: total_supply={total_supply}', TAG)

		self._name.set(_tokenName)
		self._symbol.set(_symbolName)
		self._total_supply.set(total_supply)
		self._decimals.set(_decimals)
		self._balances[self.msg.sender] = total_supply

	def on_update(self) -> None:
		super().on_update()

	@external(readonly=True)
	def name(self) -> str:
		"""
		Returns the name of the token.
		"""
		return self._name.get()

	@external(readonly=True)
	def symbol(self) -> str:
		"""
		Returns the symbol of the token.
		"""
		return self._symbol.get()

	@external(readonly=True)
	def decimals(self) -> int:
		"""
		Returns the number of decimals.
		"""
		return self._decimals.get()

	@external(readonly=True)
	def totalSupply(self) -> int:
		"""
		Returns the total number of tokens in existence.
		"""
		return self._total_supply.get()

	@external(readonly=True)
	def balanceOf(self, _owner: Address) -> int:
		"""
		Returns the amount of tokens owned by the account.

		:param _owner: The account whose balance is to be checked.
		:return Amount of tokens owned by the `account` with the given address.
		"""
		return self._balances[_owner]

	@only_owner
	@external
	def setAdmin(self, _admin: Address) -> None:
		"""
		Sets the authorized address.

		:param _admin: The authorized admin address.
		"""
		return self._admin.set(_admin)

	@external(readonly=True)
	def getAdmin(self) -> Address:
		"""
		Returns the authorized admin address.
		"""
		return self._admin.get()

	@eventlog(indexed=3)
	def Transfer(self, _from: Address, _to: Address, _value: int, _data: bytes):
		pass

	@eventlog(indexed=1)
	def Mint(self, account: Address, amount: int, _data: bytes):
		pass

	@eventlog(indexed=1)
	def Burn(self, account: Address, amount: int):
		pass

	@external
	def transfer(self, _to: Address, _value: int, _data: bytes = None):
		if _data is None:
			_data = b'None'
		staking_score = self.create_interface_score(self._staking_address.get(), stakingManagementInterface)
		if _to != self._staking_address.get():
			staking_score.transferUpdateDelegations(self.msg.sender, _to, _value)
		self._transfer(self.msg.sender, _to, _value, _data)

	def _transfer(self, _from: Address, _to: Address, _value: int, _data: bytes):
		"""
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
		"""
		if _value < 0:
			raise ZeroValueError("Transferring value cannot be less than 0.")

		if self._balances[_from] < _value:
			raise InsufficientBalanceError("Insufficient balance.")

		self._balances[_from] -= _value
		self._balances[_to] += _value

		# Emits an event log `Transfer`
		self.Transfer(_from, _to, _value, _data)

		if _to.is_contract:
			"""
			If the recipient is SCORE,
			then calls `tokenFallback` to hand over control.
			"""
			recipient_score = self.create_interface_score(_to, TokenFallbackInterface)
			recipient_score.tokenFallback(_from, _value, _data)

	@only_admin
	def _mint(self, account: Address, amount: int, _data: bytes) -> None:
		"""
		Creates amount number of tokens, and assigns to account
		Increases the balance of that account and total supply.
		This is an internal function

		:param account: The account at which token is to be created.
		:param amount: Number of tokens to be created at the `account`.
		:param _data: Any information or message

		Raises
		ZeroValueError
			if the `amount` is less than or equal to zero.
		"""

		if amount <= 0:
			raise ZeroValueError("Invalid Value")

		self._total_supply.set(self._total_supply.get() + amount)
		self._balances[account] += amount

		# Emits an event log Mint
		self.Mint(account, amount, _data)

		# Emits an event log `Transfer`
		self.Transfer(EOA_ZERO, account, amount, _data)

		if account.is_contract:
			"""
			If the recipient is SCORE,
			then calls `tokenFallback` to hand over control.
			"""
			recipient_score = self.create_interface_score(account, TokenFallbackInterface)
			recipient_score.tokenFallback(EOA_ZERO, amount, _data)

	@only_admin
	def _burn(self, account: Address, amount: int) -> None:
		"""
		Destroys `amount` number of tokens from `account`
		Decreases the balance of that `account` and total supply.
		This is an internal function

		:param account: The account at which token is to be destroyed.
		:param amount: The `amount` of tokens of `account` to be destroyed.

		Raises
		ZeroValueError
			if the `amount` is less than or equal to zero
		"""

		if amount <= 0:
			raise ZeroValueError("Invalid Value")

		self._total_supply.set(self._total_supply.get() - amount)
		self._balances[account] -= amount

		# Emits an event log Burn
		self.Burn(account, amount)

		# Emits an event log `Transfer`
		self.Transfer(account, EOA_ZERO, amount, b'None')
