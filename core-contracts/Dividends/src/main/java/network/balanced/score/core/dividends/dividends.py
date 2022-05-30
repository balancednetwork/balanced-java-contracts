# Copyright 2021 Balanced DAO
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from .utils.checks import *
from .utils.consts import *
from .utils.arraydb_helpers import *

TAG = 'Balanced Dividends'

UNITS_PER_TOKEN = 10 ** 18


class DistPercentDict(TypedDict):
    category: str
    dist_percent: int


# An interface of token to get balances.
class TokenInterface(InterfaceScore):
    @interface
    def balanceOf(self, _owner: Address) -> int:
        pass


# An interface tp the Loans SCORE to get the collateral token addresses.
class LoansInterface(InterfaceScore):
    @interface
    def getCollateralTokens(self) -> dict:
        pass

    @interface
    def getAssetTokens(self) -> dict:
        pass

    @interface
    def getDebts(self, _address_list: List[str], _day: int) -> dict:
        pass

    @interface
    def getDay(self) -> int:
        pass


class DexInterface(InterfaceScore):
    @interface
    def getTotalValue(self, _name: str, _snapshot_id: int) -> int:
        pass

    @interface
    def getBalnSnapshot(self, _name: str, _snapshot_id: int) -> int:
        pass

    @interface
    def getDataBatch(self, _name: str, _snapshot_id: int, _limit: int, _offset: int = 0) -> dict:
        pass

    @interface
    def balanceOfAt(self, _account: Address, _id: int, _snapshot_id: int, _twa: bool = False) -> int:
        pass

    @interface
    def totalSupplyAt(self, _id: int, _snapshot_id: int, _twa: bool = False) -> int:
        pass

    @interface
    def totalBalnAt(self, _id: int, _snapshot_id: int, _twa: bool = False) -> int:
        pass

    @interface
    def getTimeOffset(self) -> int:
        pass


class BalnTokenInterface(InterfaceScore):

    @interface
    def getStakeUpdates(self) -> dict:
        pass

    @interface
    def clearYesterdaysStakeChanges(self) -> bool:
        pass

    @interface
    def switchStakeUpdateDB(self) -> None:
        pass

    @interface
    def balanceOf(self, _owner: Address) -> int:
        pass

    @interface
    def stakedBalanceOfAt(self, _account: Address, _day: int) -> int:
        pass

    @interface
    def totalStakedBalanceOfAt(self, _day: int) -> int:
        pass


class IRC2Interface(InterfaceScore):

    @interface
    def transfer(self, _to: Address, _value: int, _data: bytes = None):
        pass


class Dividends(IconScoreBase):

    @eventlog(indexed=3)
    def Transfer(self, _from: Address, _to: Address, _value: int, _data: bytes):
        pass

    @eventlog(indexed=2)
    def FundTransfer(self, destination: Address, amount: int, note: str):
        pass

    @eventlog(indexed=2)
    def TokenTransfer(self, recipient: Address, amount: int, note: str):
        pass

    _GOVERNANCE = 'governance'
    _ADMIN = 'admin'
    _LOANS_SCORE = 'loans_score'
    _DAOFUND = 'daofund'
    _BALN_SCORE = "baln_score"
    _DEX_SCORE = "dex_score"

    _ACCEPTED_TOKENS = "accepted_tokens"
    _AMOUNT_TO_DISTRIBUTE = "amount_to_distribute"
    _AMOUNT_BEING_DISTRIBUTED = "amount_being_distributed"

    _BALN_DIST_INDEX = "baln_dist_index"

    _STAKED_BALN_HOLDERS = "staked_baln_holders"
    _STAKED_DIST_INDEX = "staked_dist_index"

    _BALN_IN_DEX = "baln_in_dex"
    _TOTAL_LP_TOKENS = "total_lp_tokens"
    _LP_HOLDERS_INDEX = "lp_holders_index"

    _USERS_BALANCE = "users_balance"

    _DIVIDENDS_DISTRIBUTION_STATUS = "dividends_distribution_status"

    _SNAPSHOT_ID = "snapshot_id"

    _AMOUNT_RECEIVED_STATUS = "amount_received_status"
    _DAILY_FEES = "daily_fees"

    _MAX_LOOP_COUNT = "max_loop_count"
    _MINIMUM_ELIGIBLE_DEBT = "minimum_eligible_debt"

    _DIVIDENDS_CATEGORIES = "dividends_categories"
    _DIVIDENDS_PERCENTAGE = "dividends_percentage"

    _DISTRIBUTION_ACTIVATE = "distribution_activate"

    _DIVIDENDS_BATCH_SIZE = "dividends_batch_size"

    _CLAIMED_BIT_MAP = "claimed_bit_map_"
    _TIME_OFFSET = "time_offset"
    _DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY = "dividends_enabled_to_staked_baln_only_day"

    def __init__(self, db: IconScoreDatabase) -> None:
        super().__init__(db)

        # Addresses of other SCORES, that dividends score interacts with
        self._governance = VarDB(self._GOVERNANCE, db, value_type=Address)
        self._admin = VarDB(self._ADMIN, db, value_type=Address)
        self._loans_score = VarDB(self._LOANS_SCORE, db, value_type=Address)
        self._daofund = VarDB(self._DAOFUND, db, value_type=Address)
        self._baln_score = VarDB(self._BALN_SCORE, db, value_type=Address)
        self._dex_score = VarDB(self._DEX_SCORE, db, value_type=Address)

        # Accepted tokens store all the tokens that dividends can distribute and accept, store cx00.. for ICX
        self._accepted_tokens = ArrayDB(self._ACCEPTED_TOKENS, db, value_type=Address)
        # Amount that comes in between distribution or during the week is recorded here
        self._amount_to_distribute = DictDB(self._AMOUNT_TO_DISTRIBUTE, db, value_type=int)
        # Amount that is being distributed is recorded here
        self._amount_being_distributed = DictDB(self._AMOUNT_BEING_DISTRIBUTED, db, value_type=int)

        self._baln_dist_index = VarDB(self._BALN_DIST_INDEX, db, value_type=str)

        self._staked_dist_index = VarDB(self._STAKED_DIST_INDEX, db, value_type=str)

        self._baln_in_dex = VarDB(self._BALN_IN_DEX, db, value_type=int)
        self._total_lp_tokens = VarDB(self._TOTAL_LP_TOKENS, db, value_type=int)
        self._lp_holders_index = VarDB(self._LP_HOLDERS_INDEX, db, value_type=int)

        self._users_balance = DictDB(self._USERS_BALANCE, db, value_type=int, depth=2)

        self._dividends_distribution_status = VarDB(self._DIVIDENDS_DISTRIBUTION_STATUS, db, value_type=int)

        # Track which snapshot has been used for distribution
        self._snapshot_id = VarDB(self._SNAPSHOT_ID, db, value_type=int)

        self._amount_received_status = VarDB(self._AMOUNT_RECEIVED_STATUS, db, value_type=bool)
        self._daily_fees = DictDB(self._DAILY_FEES, db, value_type=int, depth=2)

        self._max_loop_count = VarDB(self._MAX_LOOP_COUNT, db, value_type=int)
        self._minimum_eligible_debt = VarDB(self._MINIMUM_ELIGIBLE_DEBT, db, value_type=int)

        self._dividends_categories = ArrayDB(self._DIVIDENDS_CATEGORIES, db, value_type=str)
        self._dividends_percentage = DictDB(self._DIVIDENDS_PERCENTAGE, db, value_type=int)
        self._snapshot_dividends = DictDB('snapshot_dividends', db, value_type=int, depth=3)
        self._total_snapshots = DictDB("total_snapshots", db, value_type=int)
        self._complete_dividends_categories = ArrayDB('complete_dividends_categories', db, value_type=str)
        self._distribution_activate = VarDB(self._DISTRIBUTION_ACTIVATE, db, value_type=bool)

        self._dividends_batch_size = VarDB(self._DIVIDENDS_BATCH_SIZE, db, value_type=int)

        self._time_offset = VarDB(self._TIME_OFFSET, db, value_type=int)
        self._dividends_enabled_to_staked_baln_day = VarDB(self._DIVIDENDS_ENABLED_TO_STAKED_BALN_ONLY_DAY, db,
                                                           value_type=int)

    def on_install(self, _governance: Address) -> None:
        super().on_install()
        self._governance.set(_governance)

        self._accepted_tokens.put(ZERO_SCORE_ADDRESS)
        self._snapshot_id.set(1)
        self._max_loop_count.set(MAX_LOOP)
        self._minimum_eligible_debt.set(MINIMUM_ELIGIBLE_DEBT)
        self._add_initial_categories()
        self._distribution_activate.set(False)
        self._dividends_batch_size.set(50)

    def on_update(self) -> None:
        super().on_update()

    def _upate_addCategoriesToArrayDB(self) -> None:
        for categories in self._dividends_categories:
            self._complete_dividends_categories.put(categories)

    def _set_time_offset(self) -> None:
        _dex = self.create_interface_score(self._dex_score.get(), DexInterface)
        offset_time = _dex.getTimeOffset()
        self._time_offset.set(offset_time)

    def _add_initial_categories(self):
        self._dividends_categories.put(DAOFUND)
        self._dividends_categories.put(BALN_HOLDERS)
        self._dividends_percentage[DAOFUND] = 4 * 10 ** 17
        self._dividends_percentage[BALN_HOLDERS] = 6 * 10 ** 17

    @external(readonly=True)
    def name(self) -> str:
        return "Balanced Dividends"

    @external(readonly=True)
    def getDistributionActivationStatus(self) -> bool:
        return self._distribution_activate.get()

    @external
    @only_governance
    def setDistributionActivationStatus(self, _status: bool) -> None:
        self._distribution_activate.set(_status)

    @external
    @only_owner
    def setGovernance(self, _address: Address) -> None:
        self._governance.set(_address)

    @external(readonly=True)
    def getGovernance(self) -> Address:
        return self._governance.get()

    @external
    @only_governance
    def setAdmin(self, _address: Address) -> None:
        self._admin.set(_address)

    @external(readonly=True)
    def getAdmin(self) -> Address:
        return self._admin.get()

    @external
    @only_admin
    def setLoans(self, _address: Address) -> None:
        self._loans_score.set(_address)

    @external(readonly=True)
    def getLoans(self) -> Address:
        return self._loans_score.get()

    @external
    @only_admin
    def setDaofund(self, _address: Address) -> None:
        self._daofund.set(_address)

    @external(readonly=True)
    def getDaofund(self) -> Address:
        return self._daofund.get()

    @external
    @only_admin
    def setBaln(self, _address: Address) -> None:
        if not _address.is_contract:
            revert(f"{TAG}: Address provided is EOA address. Should be a contract address.")
        self._baln_score.set(_address)

    @external(readonly=True)
    def getBaln(self) -> Address:
        return self._baln_score.get()

    @external
    @only_admin
    def setDex(self, _address: Address) -> None:
        if not _address.is_contract:
            revert(f"{TAG}: Address provided is EOA address. Should be a contract address.")
        self._dex_score.set(_address)

    @external(readonly=True)
    def getDex(self) -> Address:
        return self._dex_score.get()

    @external
    @only_admin
    def setDividendsOnlyToStakedBalnDay(self, _day: int) -> None:
        if _day <= self._snapshot_id.get():
            revert(f"{TAG}: Day should be greater than the current snapshot ID.")
        self._dividends_enabled_to_staked_baln_day.set(_day)

    @external(readonly=True)
    def getDividendsOnlyToStakedBalnDay(self) -> int:
        return self._dividends_enabled_to_staked_baln_day.get()

    @external(readonly=True)
    def getBalances(self) -> dict:
        loans = self.create_interface_score(self._loans_score.get(), LoansInterface)
        assets = loans.getAssetTokens()
        balances = {}
        for symbol in assets:
            token = self.create_interface_score(Address.from_string(assets[symbol]), TokenInterface)
            balance = token.balanceOf(self.address)
            if balance > 0:
                balances[symbol] = balance
        balance = self.icx.get_balance(self.address)
        balances['ICX'] = balance
        return balances

    @external(readonly=True)
    def getDailyFees(self, _day: int) -> dict:
        fees = {}
        for token in self._accepted_tokens:
            fees[str(token)] = self._daily_fees[_day][str(token)]
        return fees

    @external(readonly=True)
    def getAcceptedTokens(self) -> list:
        """
        Returns the list of accepted tokens for dividends, zero score address represents ICX
        """
        return [token for token in self._accepted_tokens]

    @only_governance
    @external
    def addAcceptedTokens(self, _token: Address) -> None:
        if not _token.is_contract:
            revert(f"{TAG}: {_token} is not a contract address")
        if _token not in self._accepted_tokens:
            self._accepted_tokens.put(_token)

    @external(readonly=True)
    def getDividendsCategories(self) -> list:
        return [item for item in self._dividends_categories]

    @external
    @only_admin
    def addDividendsCategory(self, _category: str) -> None:
        if _category in self._dividends_categories:
            revert(f"{TAG}: {_category} is already added")
        self._dividends_categories.put(_category)
        if _category not in self._complete_dividends_categories:
            self._complete_dividends_categories.put(_category)

    @external
    @only_admin
    def removeDividendsCategory(self, _category: str) -> None:
        currentDay = self.getDay()
        if _category not in self._dividends_categories:
            revert(f"{TAG}: {_category} not found in the list of dividends categories")
        dividends_dist = self.dividendsAt(currentDay)
        if dividends_dist[_category] != 0:
            revert(f"{TAG}: Please make the category percentage to 0 before removing")
        remove_from_arraydb(_category, self._dividends_categories)

    @external(readonly=True)
    def getDividendsPercentage(self) -> dict:
        currentDay = self.getDay()
        return self.dividendsAt(currentDay)

    @external
    @only_admin
    def setDividendsCategoryPercentage(self, _dist_list: List[DistPercentDict]) -> None:
        total_percentage = 0
        if len(_dist_list) != len(self._dividends_categories):
            revert(f"{TAG}: Categories count mismatched!")
        for idx, dist_percent in enumerate(_dist_list):
            category = dist_percent["category"]
            percent = dist_percent["dist_percent"]
            if category not in self._dividends_categories:
                revert(f"{TAG}: {category} is not a valid dividends category")
            self._update_dividends_snapshot(category, percent)
            total_percentage += percent

        if total_percentage != 10 ** 18:
            revert(f"{TAG}: Total percentage doesn't sum up to 100 i.e. 10**18")

    @external(readonly=True)
    def getDividendsBatchSize(self) -> int:
        return self._dividends_batch_size.get()

    @external
    @only_admin
    def setDividendsBatchSize(self, _size: int) -> None:
        if _size <= 0:
            revert(f"{TAG}: Size can't be negative or zero")

        self._dividends_batch_size.set(_size)

    @external(readonly=True)
    def getSnapshotId(self) -> int:
        return self._snapshot_id.get()

    @external(readonly=True)
    def getDay(self) -> int:
        return (self.now() - self._time_offset.get()) // U_SECONDS_DAY

    @external(readonly=True)
    def getTimeOffset(self) -> int:
        return self._time_offset.get()

    @external
    def distribute(self) -> bool:
        """
        Main method to handle the distribution of tokens to eligible BALN token holders
        :return: True if distribution has completed
        """
        self._check_for_new_day()
        return True

    def _check_for_new_day(self):
        if self._time_offset.get() == 0:
            self._set_time_offset()
        current_snapshot_id = self.getDay()
        if self._snapshot_id.get() < current_snapshot_id:
            self._snapshot_id.set(current_snapshot_id)

    @external
    def transferDaofundDividends(self, _start: int = 0, _end: int = 0) -> None:
        if not self._distribution_activate.get():
            revert("Balanced Dividends: Distribution is not activated. Can't transfer")

        start, end = self._check_start_end(_start, _end)

        total_dividends = {}
        for day in range(start, end):
            dividends = self._get_dividends_for_daofund(day)
            if dividends:
                self._set_claimed(self._daofund.get(), day)
            total_dividends = self._add_dividends(total_dividends, dividends)

        try:
            for token in self._accepted_tokens:
                if total_dividends.get(str(token), 0) > 0:
                    if str(token) == str(ZERO_SCORE_ADDRESS):
                        self._send_ICX(self._daofund.get(), total_dividends[str(token)], "Daofund dividends")
                    else:
                        self._send_token(self._daofund.get(), total_dividends[str(token)], token, "Daofund dividends")
            self.Claimed(self._daofund.get(), start, end, str(total_dividends))
        except Exception:
            revert(f"Balanced Dividends: Error in transferring daofund dividends.")

    @external
    def claim(self, _start: int = 0, _end: int = 0) -> None:
        """
        Used to claim all fees generated by the Balanced system. _start and _end is used to take the range of dividends
        user wants to claim.
        """
        if not self._distribution_activate.get():
            revert(f"Balanced Dividends: Claim has not been activated")

        _start, _end = self._check_start_end(_start, _end)
        _account = self.msg.sender

        total_dividends = {}
        for day in range(_start, _end):
            dividends = self._get_dividends_for_day(_account, day)
            if dividends:
                self._set_claimed(_account, day)
            total_dividends = self._add_dividends(total_dividends, dividends)

        try:
            for token in self._accepted_tokens:
                if total_dividends.get(str(token), 0) > 0:
                    if str(token) == str(ZERO_SCORE_ADDRESS):
                        self._send_ICX(_account, total_dividends[str(token)], "User dividends")
                    else:
                        self._send_token(_account, total_dividends[str(token)], token, "User dividends")
            self.Claimed(_account, _start, _end, str(total_dividends))
        except Exception:
            revert(f"Balanced Dividends: Error in claiming dividends.")

    def _send_ICX(self, _to: Address, amount: int, msg: str) -> None:
        """
        Sends ICX to an address.
        :param _to: ICX destination address.
        :type _to: :class:`iconservice.base.address.Address`
        :param amount: Number of ICX sent.
        :type amount: int
        :param msg: Message for the event log.
        :type msg: str
        """
        try:
            self.icx.transfer(_to, amount)
            self.FundTransfer(_to, amount, msg + f' {amount} ICX sent to {_to}.')
        except Exception:
            revert(f'{amount} ICX not sent to {_to}.')

    def _send_token(self, _to: Address, _amount: int, _token: Address, _msg: str) -> None:
        """
        Sends IRC2 token to address
        :param _to: Destination address
        :param _amount: amount to distribute
        :param _token: Address of IRC2 token
        :param _msg: Any message to attach with transfer
        """
        try:
            token_score = self.create_interface_score(_token, IRC2Interface)
            token_score.transfer(_to, _amount)
            self.FundTransfer(_to, _amount, _msg + f" {_amount} token sent to {_to}")
        except Exception:
            revert(f"{TAG}: {_amount} token not sent to {_to}. Token: {_token}.")

    @external
    def tokenFallback(self, _from: Address, _value: int, _data: bytes) -> None:
        """
        Used only to receive all fees generated by the Balanced system.
        :param _from: Token origination address.
        :type _from: :class:`iconservice.base.address.Address`
        :param _value: Number of tokens sent.
        :type _value: int
        :param _data: Unused, ignored.
        :type _data: bytes
        """
        if self.msg.sender not in self._accepted_tokens:
            loans = self.create_interface_score(self._loans_score.get(), LoansInterface)
            available_tokens = loans.getAssetTokens()
            if str(self.msg.sender) in available_tokens.values():
                self._accepted_tokens.put(self.msg.sender)
        self._check_for_new_day()
        day = self._snapshot_id.get()
        self._daily_fees[day][str(self.msg.sender)] += _value
        self.DividendsReceivedV2(_value, self._snapshot_id.get(),
                                 f"{_value} tokens received as dividends token: {self.msg.sender} ")
        self._amount_received_status.set(True)

    @payable
    def fallback(self):
        self._check_for_new_day()
        day = self._snapshot_id.get()
        self._daily_fees[day][str(ZERO_SCORE_ADDRESS)] += self.msg.value
        self.DividendsReceivedV2(self.msg.value, self._snapshot_id.get(),
                                 f"{self.msg.value} ICX received as dividends.")
        self._amount_received_status.set(True)

    @external(readonly=True)
    def getUserDividends(self, _account: Address, _start: int = 0, _end: int = 0) -> dict:
        """
        Returns the dividends accrued by the user. Provides option to select the dividends for arbitrary days. Both
        _start and _end are inclusive. If _start or _end is only provided dividends batch size items are returned.
        [_start, _end)
        :param _account: Address of the user
        :param _start: Starting day of the dividends(inclusive)
        :param _end: Ending day of the dividends(exclusive)
        """
        
        # The platform will revert on day 1, and the frontend requires that this API returns or it will crash
        # We return an empty dict if the current day is 1, bypassing this logic

        if self._snapshot_id.get() == 1:
            return {}

        _start, _end = self._check_start_end(_start, _end)

        total_dividends = {}
        for day in range(_start, _end):
            dividends = self._get_dividends_for_day(_account, day)
            total_dividends = self._add_dividends(total_dividends, dividends)
        return total_dividends

    @external(readonly=True)
    def getDaofundDividends(self, _start: int = 0, _end: int = 0) -> dict:
        _start, _end = self._check_start_end(_start, _end)

        total_dividends = {}
        for day in range(_start, _end):
            dividends = self._get_dividends_for_daofund(day)
            total_dividends = self._add_dividends(total_dividends, dividends)
        return total_dividends

    def _check_start_end(self, _start: int, _end: int) -> (int, int):
        if _start == 0 and _end == 0:
            _end = self._snapshot_id.get()
            _start = max(1, _end - self._dividends_batch_size.get())
        elif _end == 0:
            _end = min(self._snapshot_id.get(), _start + self._dividends_batch_size.get())
        elif _start == 0:
            _start = max(1, _end - self._dividends_batch_size.get())

        if not (1 <= _start < self._snapshot_id.get()):
            revert("Invalid value of start provided")
        if not (1 < _end <= self._snapshot_id.get()):
            revert("Invalid value of end provided")
        if _start >= _end:
            revert("Start must not be greater than or equal to end.")
        if _end - _start > self._dividends_batch_size.get():
            revert(f"Maximum allowed range is {self._dividends_batch_size.get()}")
        return _start, _end

    def _get_dividends_for_day(self, _account: Address, _day: int) -> dict:
        if self._is_claimed(_account, _day):
            return {}

        baln_token_score = self.create_interface_score(self._baln_score.get(), BalnTokenInterface)
        dex_score = self.create_interface_score(self._dex_score.get(), DexInterface)

        staked_baln = baln_token_score.stakedBalanceOfAt(_account, _day)
        total_staked_baln = baln_token_score.totalStakedBalanceOfAt(_day)

        my_baln_from_pools = 0
        total_baln_from_pools = 0
        dividends_switching_day = self._dividends_enabled_to_staked_baln_day.get()

        if dividends_switching_day == 0 or _day < dividends_switching_day:
            for pool_id in (BALNBNUSD_ID, BALNSICX_ID):
                my_lp = dex_score.balanceOfAt(_account, pool_id, _day)
                total_lp = dex_score.totalSupplyAt(pool_id, _day)
                total_baln = dex_score.totalBalnAt(pool_id, _day)

                equivalent_baln = 0
                if my_lp > 0 and total_lp > 0 and total_baln > 0:
                    equivalent_baln = (my_lp * total_baln) // total_lp

                my_baln_from_pools += equivalent_baln
                total_baln_from_pools += total_baln

        my_total_baln_token = staked_baln + my_baln_from_pools
        total_baln_token = total_staked_baln + total_baln_from_pools

        my_dividends = {}
        if my_total_baln_token > 0 and total_baln_token > 0:
            dividends_distribution = self.dividendsAt(_day)
            for token in self._accepted_tokens:
                my_dividends[str(token)] = (my_total_baln_token * dividends_distribution[BALN_HOLDERS]
                                            * self._daily_fees[_day][str(token)]) // (total_baln_token * 10 ** 18)

        return my_dividends

    def _get_dividends_for_daofund(self, _day: int) -> dict:
        if self._is_claimed(self._daofund.get(), _day):
            return {}

        daofund_dividends = {}
        for token in self._accepted_tokens:
            dividends_dist = self.dividendsAt(_day)
            daofund_dividends[str(token)] = (dividends_dist[DAOFUND] * self._daily_fees[_day][str(token)]) \
                                            // 10 ** 18
        return daofund_dividends

    def _add_dividends(self, a: dict, b: dict) -> dict:
        if a and b:
            response = {}
            for token in self._accepted_tokens:
                response[str(token)] = a.get(str(token), 0) + b.get(str(token), 0)
            return response
        elif a:
            return a
        elif b:
            return b
        else:
            return {}

    def _set_claimed(self, _account: Address, _day: int):
        claimed_bit_map = DictDB(self._CLAIMED_BIT_MAP + str(_account), self.db, value_type=int)
        claimed_word_index = _day // 256
        claimed_bit_index = _day % 256
        claimed_bit_map[claimed_word_index] = claimed_bit_map[claimed_word_index] | (1 << claimed_bit_index)

    def _is_claimed(self, _account: Address, _day: int) -> bool:
        claimed_bit_map = DictDB(self._CLAIMED_BIT_MAP + str(_account), self.db, value_type=int)
        claimed_word_index = _day // 256
        claimed_bit_index = _day % 256
        claimed_word = claimed_bit_map[claimed_word_index]
        mask = (1 << claimed_bit_index)
        return claimed_word & mask == mask

    def _update_dividends_snapshot(self, _category: str, _percent: int) -> None:
        currentDay = self.getDay()
        total_snapshots_taken = self._total_snapshots[_category]

        if total_snapshots_taken > 0 and self._snapshot_dividends[_category][total_snapshots_taken - 1][
            "ids"] == currentDay:
            self._snapshot_dividends[_category][total_snapshots_taken - 1]["amount"] = _percent

        else:
            self._snapshot_dividends[_category][total_snapshots_taken]["ids"] = currentDay
            self._snapshot_dividends[_category][total_snapshots_taken]["amount"] = _percent
            self._total_snapshots[_category] = total_snapshots_taken + 1

    @external(readonly=True)
    def dividendsAt(self, _day: int) -> dict:
        if _day < 0:
            revert(f"{TAG}: "f"IRC2Snapshot: day:{_day} must be equal to or greater then Zero")
        dividends_dist = {}

        for _category in self._complete_dividends_categories:
            total_snapshots_taken = self._total_snapshots[_category]
            if total_snapshots_taken == 0:
                dividends_dist[_category] = self._dividends_percentage[_category]
                continue

            if self._snapshot_dividends[_category][total_snapshots_taken - 1]["ids"] <= _day:
                dividends_dist[_category] = self._snapshot_dividends[_category][total_snapshots_taken - 1]["amount"]
                continue

            if self._snapshot_dividends[_category][0]["ids"] > _day:
                dividends_dist[_category] = self._dividends_percentage[_category]
                continue

            low = 0
            high = total_snapshots_taken - 1
            while high > low:
                mid = high - (high - low) // 2
                mid_value = self._snapshot_dividends[_category][mid]
                if mid_value["ids"] == _day:
                    dividends_dist[_category] = mid_value["amount"]
                    continue
                elif mid_value["ids"] < _day:
                    low = mid
                else:
                    high = mid - 1

            dividends_dist[_category] = self._snapshot_dividends[_category][low]["amount"]
        dividends_dist = {k: v for k, v in dividends_dist.items() if v != 0}
        return dividends_dist

    # -------------------------------------------------------------------------------
    #   EVENT LOGS
    # -------------------------------------------------------------------------------

    @eventlog(indexed=2)
    def DividendsReceivedV2(self, _amount: int, _day: int, _data: str) -> None:
        pass

    @eventlog(indexed=1)
    def Claimed(self, _address: Address, _start: int, _end: int, _dividends: str):
        pass
