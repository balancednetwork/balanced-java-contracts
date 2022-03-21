from iconservice import *

# ================================================
#  Exceptions
# ================================================
class SenderNotScoreOwnerError(Exception):
	pass


class SenderNotAuthorized(Exception):
	pass


class SenderNotGovernance(Exception):
	pass


class NotAFunctionError(Exception):
	pass


def only_governance(func):
	if not isfunction(func):
		raise NotAFunctionError

	@wraps(func)
	def __wrapper(self: object, *args, **kwargs):
		if self.msg.sender != self._governance.get():
			raise SenderNotGovernance(self.msg.sender)

		return func(self, *args, **kwargs)
	return __wrapper

def only_admin(func):
	if not isfunction(func):
		raise NotAFunctionError

	@wraps(func)
	def __wrapper(self: object, *args, **kwargs):
		if self.msg.sender != self._admin.get():
			raise SenderNotAuthorized(self.msg.sender)

		return func(self, *args, **kwargs)
	return __wrapper

def only_owner(func):
	if not isfunction(func):
		raise NotAFunctionError

	@wraps(func)
	def __wrapper(self: object, *args, **kwargs):
		if self.msg.sender != self.owner:
			raise SenderNotScoreOwnerError(self.owner)

		return func(self, *args, **kwargs)
	return __wrapper
