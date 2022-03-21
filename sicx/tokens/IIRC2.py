from iconservice import *

# An interface of ICON Token Standard, IRC-2
class TokenStandard(ABC):
	@abstractmethod
	def name(self) -> str:
		pass

	@abstractmethod
	def symbol(self) -> str:
		pass

	@abstractmethod
	def decimals(self) -> int:
		pass

	@abstractmethod
	def totalSupply(self) -> int:
		pass

	@abstractmethod
	def balanceOf(self, _owner: Address) -> int:
		pass

	@abstractmethod
	def transfer(self, _to: Address, _value: int, _data: bytes = None):
		pass
