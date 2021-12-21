/*
 * Copyright (c) 2021-2021 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.tokens;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.iconloop.score.token.irc2.IRC2Mintable;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Statemachine Tests")
public class StateMachineTest extends TestBase {
    private static final Long WEEK = 7 * 86400L * 1000000L;
    private static final Long MAX_TIME = 4 * 365 * 86400L * 1000000L;
    private static final BigInteger MINT_AMOUNT = BigInteger.TEN.pow(40);
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    private final ArrayList<Account> accounts = new ArrayList<>();
    private final long MAXIMUM_LOCK_WEEKS = 208;
    private final long BLOCK_TIME = 2 * 1000000;

    private Score bBalnScore;
    private Score tokenScore;

    public static class BalnToken extends IRC2Mintable {
        public BalnToken(String _name, String _symbol, int _decimals) {
            super(_name, _symbol, _decimals);
        }
    }

    private static class VotingBalance {
        public BigInteger value;
        public BigInteger unlockTime;

        public VotingBalance(BigInteger value, BigInteger unlockTime) {
            this.value = value;
            this.unlockTime = unlockTime;
        }

        public VotingBalance() {
            this.value = BigInteger.ZERO;
            this.unlockTime = BigInteger.ZERO;
        }
    }

    private final Map<Account, BigInteger> tokenBalances = new HashMap<>();
    private final Map<Account, VotingBalance> votingBalances = new HashMap<>();

    @BeforeEach
    public void setup() throws Exception {
        //Deploy token contract and boosted baln contract
        tokenScore = sm.deploy(owner, BalnToken.class, "Balance Token", "BALN", 18);
        bBalnScore = sm.deploy(owner, BoostedBaln.class, tokenScore.getAddress(), "Boosted Baln", "bBALN");

        //Create 10 accounts
        int numberOfAccounts = 10;
        for (int accountNumber = 0; accountNumber < numberOfAccounts; accountNumber++) {
            Account account = sm.createAccount();
            accounts.add(account);
            tokenScore.invoke(owner, "mintTo", account.getAddress(), MINT_AMOUNT);
            tokenBalances.put(account, MINT_AMOUNT);
            votingBalances.put(account, new VotingBalance());
        }
    }

    public byte[] tokenData(String method, Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", method);
        map.put("params", params);
        JSONObject data = new JSONObject(map);
        return data.toString().getBytes();
    }

    public long addWeeksToCurrentTimestamp(long numberOfWeeks) {
        return ((sm.getBlock().getTimestamp() + numberOfWeeks * WEEK) / WEEK) * WEEK;
    }

    @DisplayName("Create Lock with")
    @Nested
    class CreateLockTests {

        private BigInteger value;
        private long unlockTime;

        @BeforeEach
        void configuration() {
            value = BigInteger.TEN.pow(20);
            long lockDuration = 5;
            unlockTime = addWeeksToCurrentTimestamp(lockDuration);
        }

        @DisplayName("zero locked amount")
        @Test
        void createLockTest() {
            // Check for transferring zero baln to create lock
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(0),
                    "transfer", bBalnScore.getAddress(), BigInteger.ZERO, tokenData("createLock", Map.of("unlockTime"
                            , unlockTime))));
            assertEquals("Token Fallback: Token value should be a positive number", e.getMessage());
            votingBalances.put(accounts.get(0), new VotingBalance(BigInteger.ZERO, BigInteger.valueOf(unlockTime)));
        }

        @DisplayName("unlock time less than current time")
        @Test
        void unlockTimeLessThanCurrentTime() {
            // Create lock in past
            final Long unlockTimeLessThanBlockTime = addWeeksToCurrentTimestamp(-2);
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(0),
                    "transfer", bBalnScore.getAddress(), value, tokenData("createLock", Map.of("unlockTime",
                            unlockTimeLessThanBlockTime))));
            assertEquals("Create Lock: Can only lock until time in the future", e.getMessage());
            votingBalances.put(accounts.get(0), new VotingBalance(value, BigInteger.ZERO));
        }

        @DisplayName("unlock time greater than max unlock time")
        @Test
        void lockGreaterThanMaxTime() {
            // Create lock with greater than max time
            // 208 weeks is the maximum someone can lock tokens
            final long unlockTimeGreaterThanMaxTime = addWeeksToCurrentTimestamp(MAXIMUM_LOCK_WEEKS + 1);
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(1),
                    "transfer", bBalnScore.getAddress(), value, tokenData("createLock", Map.of("unlockTime",
                            unlockTimeGreaterThanMaxTime))));
            assertEquals("Create Lock: Voting Lock can be 4 years max", e.getMessage());
            votingBalances.put(accounts.get(1), new VotingBalance(value, BigInteger.ZERO));
        }

        @DisplayName("existing lock")
        @Test
        void lockWithExistingLock() {
            tokenScore.invoke(accounts.get(1), "transfer", bBalnScore.getAddress(), value, tokenData("createLock",
                    Map.of("unlockTime", unlockTime)));
            votingBalances.put(accounts.get(1), new VotingBalance(value, BigInteger.valueOf(unlockTime)));

            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(1),
                    "transfer", bBalnScore.getAddress(), value, tokenData("createLock", Map.of("unlockTime",
                            unlockTime))));
            assertEquals("Create Lock: Withdraw old tokens first", e.getMessage());

            votingBalances.put(accounts.get(1), new VotingBalance(value.add(value), BigInteger.valueOf(unlockTime)));
        }

        @DisplayName("Minimum amount test")
        @Test
        void minimumAmount() {
            //Minimum amount to lock is MAX_TIME. If less than minimum amount is provided, balance is zero but the
            // transaction is not reverted.
            tokenScore.invoke(accounts.get(1), "transfer", bBalnScore.getAddress(),
                    BigInteger.valueOf(MAX_TIME - 1), tokenData("createLock",
                            Map.of("unlockTime", unlockTime)));
            assertEquals(BigInteger.ZERO, bBalnScore.call("balanceOf", accounts.get(1).getAddress(),
                    BigInteger.ZERO));
            votingBalances.put(accounts.get(1), new VotingBalance(BigInteger.valueOf(MAX_TIME - 1),
                    BigInteger.valueOf(unlockTime)));

            tokenScore.invoke(accounts.get(2), "transfer", bBalnScore.getAddress(),
                    BigInteger.valueOf(MAX_TIME),
                    tokenData("createLock", Map.of("unlockTime", unlockTime)));
            assert (((BigInteger) bBalnScore.call("balanceOf", accounts.get(2).getAddress(), BigInteger.ZERO)).compareTo(BigInteger.ZERO) > 0);
            votingBalances.put(accounts.get(2), new VotingBalance(BigInteger.valueOf(MAX_TIME),
                    BigInteger.valueOf(unlockTime)));
        }

        @DisplayName("Locked balance deducted from user's account")
        @Test
        void multipleLocks() {
            for (Account account : accounts) {
                tokenScore.invoke(account, "transfer", bBalnScore.getAddress(), value,
                        tokenData("createLock", Map.of("unlockTime", unlockTime)));
                votingBalances.put(account, new VotingBalance(value, BigInteger.valueOf(unlockTime)));
            }
        }
    }

    @DisplayName("Increase Amount")
    @Nested
    class IncreaseAmountTests {

        private BigInteger value;
        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 BALN tokens from account 0")
        @BeforeEach
        void createLock() {
            long unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            value = BigInteger.TEN.pow(21);
            tokenScore.invoke(accounts.get(0), "transfer", bBalnScore.getAddress(), value,
                    tokenData("createLock", Map.of("unlockTime", unlockTime)));
            votingBalances.put(accounts.get(0), new VotingBalance(value, BigInteger.valueOf(unlockTime)));
        }

        //Increase amount with zero value
        @DisplayName("with zero value")
        @Test
        void increaseAmountWithZeroValue() {
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(0),
                    "transfer", bBalnScore.getAddress(), BigInteger.ZERO, tokenData("increaseAmount", Map.of())));
            assertEquals("Token Fallback: Token value should be a positive number", e.getMessage());
        }

        //Increasing amount with no existing lock
        @DisplayName("for non existing lock in account 1")
        @Test
        void increaseAmountForNonExistingLock() {
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(1),
                    "transfer", bBalnScore.getAddress(), value, tokenData("increaseAmount",
                            Map.of())));
            assertEquals("Increase amount: No existing lock found", e.getMessage());
            VotingBalance vote = votingBalances.getOrDefault(accounts.get(1), new VotingBalance());
            vote.value = vote.value.add(value);
            votingBalances.put(accounts.get(1), vote);
        }

        @DisplayName("to an expired lock")
        @Test
        void increaseAmountToExpiredLock() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp())/BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bBalnScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> tokenScore.invoke(accounts.get(0),
                    "transfer", bBalnScore.getAddress(), value, tokenData("increaseAmount", Map.of())));
            assertEquals("Increase amount: Cannot add to expired lock. Withdraw", e.getMessage());
            VotingBalance vote = votingBalances.getOrDefault(accounts.get(0), new VotingBalance());
            vote.value = vote.value.add(value);
            votingBalances.put(accounts.get(0), vote);
        }

        @DisplayName("with valid data")
        @Test
        void increaseAmountWithValidData() {
            tokenScore.invoke(accounts.get(0), "transfer", bBalnScore.getAddress(), value, tokenData("increaseAmount"
                    , Map.of()));
            VotingBalance vote = votingBalances.getOrDefault(accounts.get(0), new VotingBalance());
            vote.value = vote.value.add(value);
            votingBalances.put(accounts.get(0), vote);
        }
    }

    @DisplayName("Increase Unlock time ")
    @Nested
    class IncreaseUnlockTimeTests {

        private long unlockTime;
        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 BALN tokens from account 0")
        @BeforeEach
        void createLock() {
            unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            BigInteger value = BigInteger.TEN.pow(21);
            tokenScore.invoke(accounts.get(0), "transfer", bBalnScore.getAddress(), value,
                    tokenData("createLock", Map.of("unlockTime", unlockTime)));
            votingBalances.put(accounts.get(0), new VotingBalance(value, BigInteger.valueOf(unlockTime)));
        }

        @DisplayName("of non existing lock account")
        @Test
        void increaseUnlockTimeNonExisting() {
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> bBalnScore.invoke(accounts.get(2),
                    "increaseUnlockTime", BigInteger.valueOf(unlockTime)));
            assertEquals("Increase unlock time: Nothing is locked", e.getMessage());
        }

        @DisplayName("of expired lock")
        @Test
        void increaseUnlockTimeExpiredLock() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp())/BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bBalnScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            //Update unlock time
            unlockTime = addWeeksToCurrentTimestamp(5);
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> bBalnScore.invoke(accounts.get(0),
                    "increaseUnlockTime", BigInteger.valueOf(unlockTime)));
            assertEquals("Increase unlock time: Lock expired", e.getMessage());
        }

        @DisplayName("with unlock time less than the current unlock time")
        @Test
        void decreaseUnlockTime() {
            Map<String, BigInteger> locked = (Map<String, BigInteger>) bBalnScore.call("getLocked",
                    accounts.get(0).getAddress());
            final BigInteger unlockTime = locked.get("end").subtract(BigInteger.valueOf(2 * WEEK));

            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> bBalnScore.invoke(accounts.get(0),
                    "increaseUnlockTime", unlockTime));
            assertEquals("Increase unlock time: Can only increase lock duration", e.getMessage());
        }

        @DisplayName("with unlock time more than max time")
        @Test
        void increaseUnlockTimeGreaterThanMaxTime() {
            final long unlockTime = addWeeksToCurrentTimestamp(MAXIMUM_LOCK_WEEKS + 1);

            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> bBalnScore.invoke(accounts.get(0),
                    "increaseUnlockTime", BigInteger.valueOf(unlockTime)));
            assertEquals("Increase unlock time: Voting lock can be 4 years max", e.getMessage());
        }

        @DisplayName("from contract")
        @Test
        void increaseUnlockFromContract() {
            AssertionError e = Assertions.assertThrows(AssertionError.class,
                    () -> bBalnScore.invoke(Account.getAccount(Account.newScoreAccount(500).getAddress()),
                            "increaseUnlockTime", BigInteger.valueOf(unlockTime)));
            assertEquals("Assert Not contract: Smart contract depositors not allowed", e.getMessage());
        }

        @DisplayName("with valid data")
        @Test
        void increaseUnlockWithValidData() {
            long increasedUnlockTime = addWeeksToCurrentTimestamp(10);

            bBalnScore.invoke(accounts.get(0), "increaseUnlockTime", BigInteger.valueOf(increasedUnlockTime));
            VotingBalance vote = votingBalances.getOrDefault(accounts.get(0), new VotingBalance());
            vote.unlockTime = BigInteger.valueOf(increasedUnlockTime);
            votingBalances.put(accounts.get(0), vote);
        }
    }

    @DisplayName("Withdraw tokens from the voting escrow")
    @Nested
    class WithdrawLockTests {

        private final long lockedWeeks = 5;

        @DisplayName("Create Lock of 1000 BALN tokens from account 0")
        @BeforeEach
        void createLock() {
            long unlockTime = addWeeksToCurrentTimestamp(lockedWeeks);
            BigInteger value = BigInteger.TEN.pow(21);
            tokenScore.invoke(accounts.get(0), "transfer", bBalnScore.getAddress(), value,
                    tokenData("createLock", Map.of("unlockTime", unlockTime)));
            votingBalances.put(accounts.get(0), new VotingBalance(value, BigInteger.valueOf(unlockTime)));
        }

        @DisplayName("before unlock expires")
        @Test
        void unlockBeforeExpiry() {
            AssertionError e = Assertions.assertThrows(AssertionError.class, () -> bBalnScore.invoke(accounts.get(0),
                    "withdraw"));
            assertEquals("Withdraw: The lock didn't expire", e.getMessage());
        }

        @DisplayName("after the expiry")
        @Test
        void unlockAfterExpiry() {
            long deltaBlock = (addWeeksToCurrentTimestamp(lockedWeeks) - sm.getBlock().getTimestamp())/BLOCK_TIME + 1;
            sm.getBlock().increase(deltaBlock);
            // Check if the lock time has expired
            assertEquals(BigInteger.ZERO, bBalnScore.call("balanceOf", accounts.get(0).getAddress(), BigInteger.ZERO));

            bBalnScore.invoke(accounts.get(0), "withdraw");
            assertEquals(MINT_AMOUNT, tokenScore.call("balanceOf", accounts.get(0).getAddress()));
            votingBalances.put(accounts.get(0), new VotingBalance());
        }
    }

    @DisplayName("Checkpoint")
    @Test
    void checkpoint() {
        for (Account account : accounts) {
            bBalnScore.invoke(account, "checkpoint");
        }
    }

    @DisplayName("Advance Clock")
    @Test
    void advanceClock() {
        long sleepDuration = 40;
        sm.getBlock().increase(sleepDuration);
    }

    @DisplayName("Verify token balances")
    @AfterEach
    void verifyTokenBalances() {
        // Verify that token balances are correct
        for (Account account : accounts) {
            assertEquals(tokenScore.call("balanceOf", account.getAddress()),
                    MINT_AMOUNT.subtract(votingBalances.get(account).value));
        }
    }

    @DisplayName("Verify individual balance against total supply")
    @AfterEach
    void verifyTotalSupply() {
        // Verify the sum of all escrow balances is equal to the escrow totalSupply
        BigInteger currentTime = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger totalSupply = BigInteger.ZERO;
        for (Account account : accounts) {
            VotingBalance vote = votingBalances.getOrDefault(account, new VotingBalance());
            BigInteger balance = (BigInteger) bBalnScore.call("balanceOf", account.getAddress(), BigInteger.ZERO);
            totalSupply = totalSupply.add(balance);

            if (vote.unlockTime.compareTo(currentTime) > 0 && vote.value.divide(BigInteger.valueOf(MAX_TIME)).compareTo(BigInteger.ZERO) > 0) {
                assert (balance.compareTo(BigInteger.ZERO) > 0);
            } else
                assert vote.value.compareTo(BigInteger.ZERO) <= 0 && vote.unlockTime.compareTo(currentTime) > 0 || (balance.compareTo(BigInteger.ZERO) == 0);
        }
        assertEquals(bBalnScore.call("totalSupply", BigInteger.ZERO), totalSupply);
    }

    @DisplayName("Verify balanceOfAt against totalSupplyAt")
    @AfterEach
    void verifyTotalSupplyAt() {
        // Verify that total balances in account is same as total supply in previous block

        sm.getBlock().increase(16);
        BigInteger totalSupplyAtIncreasedBlock = BigInteger.ZERO;
        BigInteger balance;
        for (Account account : accounts) {
            balance = (BigInteger) bBalnScore.call("balanceOfAt", account.getAddress(),
                    BigInteger.valueOf(sm.getBlock().getHeight() - 4));
            totalSupplyAtIncreasedBlock = totalSupplyAtIncreasedBlock.add(balance);
        }
        assertEquals(bBalnScore.call("totalSupplyAt", BigInteger.valueOf(sm.getBlock().getHeight() - 4)),
                totalSupplyAtIncreasedBlock);
    }

}
