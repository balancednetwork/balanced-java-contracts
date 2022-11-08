/*
 * Copyright (c) 2022-2022 Balanced.network.
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

package network.balanced.score.core.staking;

import network.balanced.score.lib.structs.PrepDelegations;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.test.integration.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import score.Address;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.HUNDRED;
import static network.balanced.score.core.staking.utils.Constant.ONE_EXA;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StakingIntegrationTest implements ScoreIntegrationTest {

    // random prep address that is outside of top prep
    private final Address outsideTopPrep = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");

    // keep the staking address here
    private static Address stakingAddress;// = balanced.reader.staking._address();

    private static Balanced balanced;
    private static BalancedClient owner;
    private static BalancedClient user;
    private static BalancedClient reader;
    private static Address senderAddress;
    private static Address user2;
    private static BalancedClient tester;
    private static BalancedClient tester2;
    private static Address testerAddress;
    private static Address testerAddress2;


    @BeforeAll
    static void setup() throws Exception {
        balanced = new Balanced();
        balanced.setupBalanced();
        reader = balanced.newClient(BigInteger.ZERO);
        stakingAddress = balanced.staking._address();
        owner = balanced.ownerClient;
        tester = balanced.newClient();
        tester2 = balanced.newClient();
        testerAddress = tester.getAddress();
        testerAddress2 = tester2.getAddress();

        senderAddress = balanced.ownerClient.getAddress();
        user = balanced.newClient();
        user2 = balanced.newClient().getAddress();

    }

    @Test
    @Order(1)
    void testName() {
        assertEquals("Staked ICX Manager", reader.staking.name());
    }

    @Test
    @Order(2)
    void testSicxAddress() {
        Address value = reader.staking.getSicxAddress();
        assertEquals(balanced.sicx._address(), value);
    }

    @Test
    @Order(3)
    void checkTopPreps() {
        List<Address> topPrep = reader.staking.getTopPreps();
        List<Address> prepList = reader.staking.getPrepList();
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();

        BigInteger sum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            sum = sum.add(value);
        }

        assertEquals(100, topPrep.size());
        assertEquals(new BigInteger("0"), sum);
        assertEquals(100, prepList.size());
    }

    @Test
    @Order(4)
    void testStakeIcxByNewUser() {
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        owner.staking.stakeICX(new BigInteger("100").multiply(ONE_EXA), null, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), ONE_EXA);
                expectedNetworkDelegations.put(prep.toString(), ONE_EXA);
                expectedPrepDelegations.put(prep.toString(), ONE_EXA);
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)),
                reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        assertEquals(userBalance.add(new BigInteger("100").multiply(ONE_EXA)),
                reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    @Order(5)
    void testSecondStakeIcx() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger secondUserBalance = reader.sicx.balanceOf(user2);

        // stakes 200 ICX to user2
        owner.staking.stakeICX(new BigInteger("200").multiply(ONE_EXA), user2, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(user2);

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("200").multiply(ONE_EXA)),
                reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("200").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        assertEquals(userBalance, reader.sicx.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("200").multiply(ONE_EXA)),
                reader.sicx.balanceOf(user2));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    @Order(6)
    void delegate() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);

        PrepDelegations p = new PrepDelegations();
        Address delegatedAddress = reader.staking.getTopPreps().get(20);
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        // delegates to one address
        owner.staking.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("102").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("100").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("102").multiply(ONE_EXA));
                } else {
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                }
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        BigInteger currentTotalStake = reader.staking.getTotalStake();
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(currentTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(userBalance, reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    public boolean contains(Address target, List<Address> addresses) {
        for (Address address : addresses) {
            if (address.equals(target)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Order(7)
    void delegateToThreePreps() {
        PrepDelegations p = new PrepDelegations();
        PrepDelegations p2 = new PrepDelegations();
        PrepDelegations p3 = new PrepDelegations();

        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        List<Address> delegatedAddressList = reader.staking.getTopPreps();
        Address firstPrepToVote = delegatedAddressList.get(25);
        Address secondPrepToVote = delegatedAddressList.get(26);
        Address thirdPrepToVote = delegatedAddressList.get(27);

        p._address = firstPrepToVote;
        p._votes_in_per = new BigInteger("50").multiply(ONE_EXA);
        p2._address = secondPrepToVote;
        p2._votes_in_per = new BigInteger("25").multiply(ONE_EXA);
        p3._address = thirdPrepToVote;
        p3._votes_in_per = new BigInteger("25").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p, p2, p3
        };

        // delegates to three address
        owner.staking.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(secondPrepToVote.toString())) {

                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                } else if (prep.toString().equals(thirdPrepToVote.toString())) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                } else if (prep.toString().equals(firstPrepToVote.toString())) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("52").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("52").multiply(ONE_EXA));
                } else {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));

                }
            }
        }

        BigInteger prepDelegationsSum = BigInteger.ZERO;
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);


        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(userBalance, reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);


    }

    @Test
    @Order(8)
    void delegateOutsideTopPrep() {
        PrepDelegations p = new PrepDelegations();

        // random prep address generated
        p._address = outsideTopPrep;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);

        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);

        // delegates to one address
        owner.staking.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("100").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), new BigInteger("100").multiply(ONE_EXA));
            }
            if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));

            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(userBalance, reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(9)
    void stakeAfterDelegate() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);

        owner.staking.stakeICX(new BigInteger("100").multiply(ONE_EXA), null, null);

        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("200").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), new BigInteger("200").multiply(ONE_EXA));
            }
            if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("4").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);

        BigInteger newSupply = previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA));
        assertEquals(newSupply, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(newSupply, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)), reader.sicx.totalSupply());
        assertEquals(userBalance.add(new BigInteger("100").multiply(ONE_EXA)), reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @SuppressWarnings("unchecked")
    void checkNetworkDelegations(Map<String, BigInteger> expected) {
        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
        Map<String, BigInteger> networkDelegations = new java.util.HashMap<>();
        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");

        for (Map<String, Object> del : delegationList) {
            String hexValue = del.get("value").toString();
            hexValue = hexValue.replace("0x", "");
            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
        }
        assertEquals(expected, networkDelegations);

    }

    @Test
    @Order(10)
    void transferPreferenceToNoPreference() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger secondUserBalance = reader.sicx.balanceOf(user2);
        owner.sicx.transfer(user2, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(new BigInteger(
                "50").multiply(ONE_EXA));
        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations.put(prep.toString(), newUserBalance);
            }
            if (contains(prep, topPreps)) {
                user2ExpectedDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("4").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = reader.staking.getAddressDelegations(user2);

        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(newUserBalance,
                reader.sicx.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.balanceOf(user2));

        checkNetworkDelegations(expectedNetworkDelegations);

    }


    @Test
    @Order(11)
    void delegateFirstThenStake() {
        PrepDelegations p = new PrepDelegations();
        Address delegatedAddress = reader.staking.getTopPreps().get(33);

        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);

        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger testerBalance = reader.sicx.balanceOf(testerAddress);

        // delegates to one address
        tester.staking.delegate(userDelegation);
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(testerAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(delegatedAddress.toString(), BigInteger.ZERO);
        assertEquals(userDelegations, userExpectedDelegations);
        tester.staking.stakeICX(new BigInteger("50").multiply(ONE_EXA), null, null);
        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        userDelegations.clear();
        userDelegations = reader.staking.getAddressDelegations(testerAddress);

        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();


        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(),
                        testerBalance.add(new BigInteger("525").multiply(ONE_EXA).divide(BigInteger.TEN)));
                userExpectedDelegations.put(prep.toString(), testerBalance.add(new BigInteger("50").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("54").multiply(ONE_EXA));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("4").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(),
                        new BigInteger("25").multiply(ONE_EXA).divide(BigInteger.TEN));
            }
        }

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("50").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("50").multiply(ONE_EXA)), reader.sicx.totalSupply());
        assertEquals(testerBalance.add(new BigInteger("50")).multiply(ONE_EXA),
                reader.sicx.balanceOf(testerAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }


    @Test
    @Order(12)
    void transferPreferenceToPreference() {
        Address delegatedAddress = reader.staking.getTopPreps().get(33);
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger testerBalance = reader.sicx.balanceOf(testerAddress);

        owner.sicx.transfer(testerAddress, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(new BigInteger("50").multiply(ONE_EXA));
        BigInteger newSupply = testerBalance.add(new BigInteger("50").multiply(ONE_EXA));
        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations.put(prep.toString(), newUserBalance);
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(),
                        testerBalance.add(new BigInteger("525").multiply(ONE_EXA).divide(BigInteger.TEN)));
                user2ExpectedDelegations.put(prep.toString(), newSupply);
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("1035").multiply(ONE_EXA).divide(BigInteger.TEN));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(),
                        new BigInteger("25").multiply(ONE_EXA).divide(BigInteger.TEN));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = reader.staking.getAddressDelegations(testerAddress);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(newUserBalance,
                reader.sicx.balanceOf(senderAddress));
        assertEquals(newSupply,
                reader.sicx.balanceOf(testerAddress));
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, prepDelegationsSum);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(13)
    void transferNullToNull() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger testerBalance = reader.sicx.balanceOf(testerAddress);
        Address delegatedAddress = reader.staking.getTopPreps().get(33);
        Address receiverAddress = reader.staking.getTopPreps().get(43);

        tester2.staking.stakeICX(new BigInteger("100").multiply(ONE_EXA), null, null);

        tester2.sicx.transfer(receiverAddress, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();
        for (Address prep : prepList) {
            if (prep.toString().equals(outsideTopPrep.toString())) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            } else {
                BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(),
                            testerBalance.add(new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN)));
                    expectedNetworkDelegations.put(prep.toString(),
                            new BigInteger("1045").multiply(ONE_EXA).divide(BigInteger.TEN));
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                } else if (contains(prep, topPreps)) {
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                    expectedNetworkDelegations.put(prep.toString(),
                            new BigInteger("45").multiply(ONE_EXA).divide(BigInteger.TEN));
                    expectedPrepDelegations.put(prep.toString(),
                            new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                }
            }
        }

        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(testerAddress2);
        Map<String, BigInteger> userDelegations2 = reader.staking.getAddressDelegations(receiverAddress);
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(userDelegations2, user2ExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)), reader.sicx.totalSupply());
        assertEquals(new BigInteger("50").multiply(ONE_EXA),
                reader.sicx.balanceOf(receiverAddress));
        assertEquals(new BigInteger("50").multiply(ONE_EXA),
                reader.sicx.balanceOf(testerAddress2));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(14)
    void transferNullToPreference() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger testerBalance = reader.sicx.balanceOf(testerAddress);
        BigInteger testerBalance2 = reader.sicx.balanceOf(testerAddress2);
        Address delegatedAddress = reader.staking.getTopPreps().get(33);

        tester2.sicx.transfer(senderAddress, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> userExpectedDelegations2 = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        BigInteger newUserBalance = userBalance.add(new BigInteger(
                "50").multiply(ONE_EXA));
        BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations2.put(prep.toString(), newUserBalance);

            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(new BigInteger("3").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("1045").multiply(ONE_EXA).divide(BigInteger.TEN));
                userExpectedDelegations.put(prep.toString(), divide);
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("45").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), divide);
            }
        }

        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(testerAddress2);
        Map<String, BigInteger> userDelegations2 = reader.staking.getAddressDelegations(senderAddress);
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();

        assertEquals(userDelegations, new java.util.HashMap<>());
        assertEquals(userDelegations2, userExpectedDelegations2);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(testerBalance2.subtract(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.balanceOf(testerAddress2));
        assertEquals(userBalance.add(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(15)
    void unstakePartial() {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");

        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        Address delegatedAddress = reader.staking.getTopPreps().get(33);

        owner.sicx.transfer(stakingAddress, new BigInteger("100").multiply(ONE_EXA), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("1035").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(),
                        new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(new BigInteger("100").multiply(ONE_EXA)),
                reader.staking.getTotalStake());
        assertEquals(new BigInteger("100").multiply(ONE_EXA), reader.staking.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("100").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("100").multiply(ONE_EXA)),
                reader.sicx.balanceOf(senderAddress));

        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("100").multiply(ONE_EXA), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(previousTotalStake.subtract(new BigInteger("100").multiply(ONE_EXA)),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

        Map<String, Object> stakeDetails = systemScore.getStake(stakingAddress);
        List<Map<String, Object>> unstakeInfo2 = (List<Map<String, Object>>) stakeDetails.get("unstakes");
        String unstakeExpected = (String) unstakeInfo2.get(0).get("unstakeBlockHeight");
        Assertions.assertEquals(unstakeExpected, reader.staking.getUserUnstakeInfo(senderAddress).get(0).get(
                "blockHeight"));
    }

    @Test
    @Order(16)
    void unstakeFull() {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");


        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        Address delegatedAddress = reader.staking.getTopPreps().get(33);
        BigInteger previousUnstakingAmount = reader.staking.getUnstakingAmount();


        owner.sicx.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("0"));
            } else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("0"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(new BigInteger("50").multiply(ONE_EXA)),
                reader.staking.getTotalStake());
        assertEquals(previousUnstakingAmount.add(new BigInteger("50").multiply(ONE_EXA)),
                reader.staking.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("50").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4).toString());
        assertEquals(new BigInteger("50").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        assertEquals(previousTotalStake.subtract(new BigInteger("50").multiply(ONE_EXA)),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    @Order(17)
    void stakeAfterUnstake() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        owner.staking.stakeICX(new BigInteger("10").multiply(ONE_EXA), null, null);
        Assertions.assertEquals(new BigInteger("140").multiply(ONE_EXA), reader.staking.getUnstakingAmount());

        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(senderAddress);
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        Assertions.assertEquals(new BigInteger("90").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        Assertions.assertEquals(new BigInteger("10").multiply(ONE_EXA), reader.staking.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("10").multiply(ONE_EXA), reader.staking.claimableICX(senderAddress));
        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(2));
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(4));
        List<List<Object>> unstakeList = reader.staking.getUnstakeInfo();
        String hexVal = (String) unstakeList.get(1).get(1);
        hexVal = hexVal.replace("0x", "");
        Assertions.assertEquals(new BigInteger("50").multiply(ONE_EXA), new BigInteger(hexVal, 16));
        assertEquals(previousTotalStake.add(new BigInteger("10").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("10").multiply(ONE_EXA)), reader.sicx.totalSupply());
    }

    @Test
    @Order(18)
    void claimUnstakedICX() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        owner.staking.stakeICX(new BigInteger("200").multiply(ONE_EXA), null, null);
        assertEquals(previousTotalStake.add(new BigInteger("200").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("200").multiply(ONE_EXA)), reader.sicx.totalSupply());

        Assertions.assertEquals(new BigInteger("150").multiply(ONE_EXA), reader.staking.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("100").multiply(ONE_EXA), reader.staking.claimableICX(senderAddress));
        Assertions.assertEquals(new BigInteger("50").multiply(ONE_EXA), reader.staking.claimableICX(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        Assertions.assertEquals(new BigInteger("0").multiply(ONE_EXA), reader.staking.getUnstakingAmount());

        owner.staking.claimUnstakedICX(senderAddress);
        owner.staking.claimUnstakedICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        Assertions.assertEquals(reader.staking._balance(), BigInteger.ZERO);
    }


//    @Test
//    @Order(19)
//    void testMigration() throws Exception {
//
//        Address delegatedAddress = reader.staking.getTopPreps().get(20);
//        // stakes ICX
//        ((StakingScoreClient) staking).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
//                , null);
//        // Delegation is changed
//        PrepDelegations p = new PrepDelegations();
////        PrepDelegations p2=new PrepDelegations();
//
//        p._address = delegatedAddress;
//        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
//        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
//        reader.staking.delegate(userDelegation);
//
//        reader.sicx.transfer(user2, new BigInteger("50").multiply(ONE_EXA), null);
//        JSONObject data = new JSONObject();
//        data.put("method", "unstake");
//
//        reader.sicx.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
//        data.clear();
//        data.put("method", "unstake");
//
//        reader.sicx.transfer(stakingAddress, new BigInteger("2").multiply(ONE_EXA), data.toString().getBytes());
//        data.clear();
//        data.put("method", "unstake");
//        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
//        reader.sicx.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
//        data.clear();
//        data.put("method", "unstake");
//        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
//        reader.sicx.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
//        data.clear();
//        data.put("method", "unstake");
//        reader.sicx.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
//
//        ((StakingScoreClient) staking).stakeICX(new BigInteger("15000000000000000000"), null,
//                null);
//        checkReadonlyFunctions("before", delegatedAddress);
//        balanced.reader.staking._update(System.getProperty("java"), null);
//
//        test(delegatedAddress);
//        afterUpdate(delegatedAddress);
//    }

    void checkReadonlyFunctions(String flag, Address delegatedAddress) {
        assertEquals(new BigInteger("73000000000000000000"), reader.staking.getTotalStake());
        assertEquals(new BigInteger("27000000000000000000"), reader.staking.getUnstakingAmount());
        assertEquals(balanced.sicx._address(), reader.staking.getSicxAddress());
        assertEquals(new BigInteger("12000000000000000000"),
                reader.staking.claimableICX(senderAddress));
        assertEquals(new BigInteger("3000000000000000000"),
                reader.staking.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        assertEquals(new BigInteger("15000000000000000000"), reader.staking.totalClaimableIcx());
        assertEquals(new BigInteger("23000000000000000000"), reader.sicx.balanceOf(senderAddress));
        assertEquals(new BigInteger("50000000000000000000"), reader.sicx.balanceOf(user2));


        if (flag.equals("after")) {
            Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
            Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
            Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
            List<Address> prepList = reader.staking.getPrepList();
            List<Address> topPreps = reader.staking.getTopPreps();
            for (Address prep : prepList) {
                if (contains(prep, topPreps)) {
                    userExpectedDelegations.put(prep.toString(),
                            new BigInteger("23").multiply(ONE_EXA).divide(HUNDRED));
                    expectedPrepDelegations.put(prep.toString(),
                            new BigInteger("73").multiply(ONE_EXA).divide(HUNDRED));
                    expectedNetworkDelegations.put(prep.toString(),
                            new BigInteger("73").multiply(ONE_EXA).divide(HUNDRED));
                }
            }
            // get address delegations of a user
            Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
            assertEquals(userDelegations, userExpectedDelegations);
            assertEquals(reader.staking.getPrepDelegations(), expectedPrepDelegations);
            checkNetworkDelegations(expectedNetworkDelegations);
        } else {
            //checks address delegation of a sender
            checkAddressDelegations(senderAddress, delegatedAddress.toString(), new BigInteger(
                    "23000000000000000000"));

            checkPrepDelegations(delegatedAddress.toString(), new BigInteger("23500000000000000000"),
                    new BigInteger("500000000000000000"));
        }
        checkFirstUserUnstakeInfo();
        checkSecondUserUnstakeInfo();
        checkUnstakeInfo();

    }

    void checkAddressDelegations(Address senderAddress, String prep, BigInteger delegations) {
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(prep, delegations);
        assertEquals(userDelegations, userExpectedDelegations);
    }

    void checkPrepDelegations(String delegatedAddress, BigInteger specificDelegations, BigInteger evenlyDelegation) {
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();
        BigInteger sum = new BigInteger("0");

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.equals(Address.fromString(delegatedAddress))) {
                    expectedPrepDelegations.put(prep.toString(), specificDelegations);
                } else {
                    expectedPrepDelegations.put(prep.toString(), evenlyDelegation);

                }
            }
        }
        assertEquals(reader.staking.getPrepDelegations(), expectedPrepDelegations);

    }

    void checkFirstUserUnstakeInfo() {
        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10").multiply(ONE_EXA), new BigInteger(hexValue, 16));
    }

    void checkSecondUserUnstakeInfo() {
        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("7").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(1).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(1).get("from"));
        String hexVal = (String) userUnstakeInfo.get(1).get("amount");
        hexVal = hexVal.replace("0x", "");
        assertEquals(new BigInteger("10").multiply(ONE_EXA), new BigInteger(hexVal, 16));
    }

    void checkUnstakeInfo() {
        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4).toString());

        String value = (String) firstItem.get(1);
        value = value.replace("0x", "");
        assertEquals(new BigInteger("7000000000000000000"), new BigInteger(value, 16));
        List<Object> secondItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), secondItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", secondItem.get(4).toString());
        String hexVal = (String) secondItem.get(1);
        hexVal = hexVal.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexVal, 16));
        List<Object> thirdItem = unstakeInfo.get(2);
        assertEquals(senderAddress.toString(), thirdItem.get(2).toString());
        assertEquals(senderAddress.toString(), thirdItem.get(4).toString());
        String hexValue = (String) thirdItem.get(1);
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexValue, 16));
    }


    void test(Address delegatedAddress) {
        checkReadonlyFunctions("after", delegatedAddress);
    }


    void afterUpdate(Address delegatedAddress) {
        checkReadonlyFunctions("after", delegatedAddress);


        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
//         stakes ICX
        reader.staking.stakeICX(new BigInteger("227").multiply(ONE_EXA), null, null);
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("227").multiply(ONE_EXA)),
                reader.staking.getTotalStake());
        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("227").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        // Delegation is changed
        delegateAfterUpdate();
        transferAfterUpdate();
        unstakeAfterUpdate();
        stakeAfterUnstakeUpdate();
        claimAfterUpdate();

    }


    void delegateAfterUpdate() {


        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);

        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        Address delegatedAddress = reader.staking.getTopPreps().get(20);
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        // delegates to one address
        reader.staking.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(),
                            new BigInteger("2505").multiply(ONE_EXA).divide(BigInteger.TEN));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("250").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(),
                            new BigInteger("2505").multiply(ONE_EXA).divide(BigInteger.TEN));
                } else {
                    expectedNetworkDelegations.put(prep.toString(),
                            new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN));
                    expectedPrepDelegations.put(prep.toString(),
                            new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN));
                }
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        BigInteger currentTotalStake = reader.staking.getTotalStake();
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(currentTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(userBalance, reader.sicx.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }


    void transferAfterUpdate() {
        Address delegatedAddress = reader.staking.getTopPreps().get(20);
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        BigInteger testerBalance = reader.sicx.balanceOf(user2);


        reader.sicx.transfer(user2, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(new BigInteger("50").multiply(ONE_EXA));
        for (Address prep : prepList) {

            if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("201").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(),
                        userBalance.subtract(new BigInteger("50").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("201").multiply(ONE_EXA));
                user2ExpectedDelegations.put(prep.toString(), ONE_EXA);
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), ONE_EXA);
                expectedPrepDelegations.put(prep.toString(), ONE_EXA);
                user2ExpectedDelegations.put(prep.toString(), ONE_EXA);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = reader.staking.getAddressDelegations(user2);
        assertEquals(previousTotalStake, reader.staking.getTotalStake());
        assertEquals(previousTotalSupply, reader.sicx.totalSupply());
        assertEquals(newUserBalance,
                reader.sicx.balanceOf(senderAddress));
        assertEquals(testerBalance.add(new BigInteger("50").multiply(ONE_EXA)),
                reader.sicx.balanceOf(user2));
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(new BigInteger("300").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }


    @SuppressWarnings("unchecked")
    void unstakeAfterUpdate() {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");

        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        BigInteger userBalance = reader.sicx.balanceOf(senderAddress);
        Address delegatedAddress = reader.staking.getTopPreps().get(20);

        reader.sicx.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = reader.staking.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = reader.staking.getPrepList();
        List<Address> topPreps = reader.staking.getTopPreps();


        for (Address prep : prepList) {

            if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("191").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(),
                        userBalance.subtract(new BigInteger("10").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("191").multiply(ONE_EXA));
            } else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), ONE_EXA);
                expectedPrepDelegations.put(prep.toString(), ONE_EXA);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = reader.staking.getAddressDelegations(senderAddress);
        assertEquals(new BigInteger("290").multiply(ONE_EXA),
                reader.staking.getTotalStake());
        assertEquals(new BigInteger("10").multiply(ONE_EXA), reader.staking.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("10").multiply(ONE_EXA)),
                reader.sicx.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("10").multiply(ONE_EXA)),
                reader.sicx.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10").multiply(ONE_EXA), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(new BigInteger("290").multiply(ONE_EXA),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

        Map<String, Object> stakeDetails = systemScore.getStake(stakingAddress);
        List<Map<String, Object>> unstakeInfo2 = (List<Map<String, Object>>) stakeDetails.get("unstakes");
        String unstakeExpected = (String) unstakeInfo2.get(0).get("unstakeBlockHeight");
        Assertions.assertEquals(unstakeExpected, reader.staking.getUserUnstakeInfo(senderAddress).get(0).get(
                "blockHeight"));
    }


    void stakeAfterUnstakeUpdate() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        reader.staking.stakeICX(new BigInteger("5").multiply(ONE_EXA), null, null);
        Assertions.assertEquals(new BigInteger("5").multiply(ONE_EXA), reader.staking.getUnstakingAmount());

        List<Map<String, Object>> userUnstakeInfo = reader.staking.getUserUnstakeInfo(senderAddress);
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        Assertions.assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        Assertions.assertEquals(new BigInteger("5").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        Assertions.assertEquals(new BigInteger("47").multiply(ONE_EXA), reader.staking.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("27").multiply(ONE_EXA), reader.staking.claimableICX(senderAddress));
        List<List<Object>> unstakeInfo = reader.staking.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(2));
        Assertions.assertEquals(senderAddress.toString(), firstItem.get(4));
        List<List<Object>> unstakeList = reader.staking.getUnstakeInfo();
        String hexVal = (String) unstakeList.get(0).get(1);
        hexVal = hexVal.replace("0x", "");
        Assertions.assertEquals(new BigInteger("5").multiply(ONE_EXA), new BigInteger(hexVal, 16));
        assertEquals(previousTotalStake.add(new BigInteger("5").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("5").multiply(ONE_EXA)), reader.sicx.totalSupply());
    }


    void claimAfterUpdate() {
        BigInteger previousTotalStake = reader.staking.getTotalStake();
        BigInteger previousTotalSupply = reader.sicx.totalSupply();
        reader.staking.stakeICX(new BigInteger("20").multiply(ONE_EXA), null, null);
        assertEquals(previousTotalStake.add(new BigInteger("20").multiply(ONE_EXA)), reader.staking.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("20").multiply(ONE_EXA)), reader.sicx.totalSupply());

        Assertions.assertEquals(new BigInteger("52").multiply(ONE_EXA), reader.staking.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("32").multiply(ONE_EXA), reader.staking.claimableICX(senderAddress));
        Assertions.assertEquals(new BigInteger("20").multiply(ONE_EXA), reader.staking.claimableICX(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        Assertions.assertEquals(new BigInteger("0").multiply(ONE_EXA), reader.staking.getUnstakingAmount());

        reader.staking.claimUnstakedICX(senderAddress);
        reader.staking.claimUnstakedICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        Assertions.assertEquals(reader.staking._balance(), BigInteger.ZERO);
    }
}