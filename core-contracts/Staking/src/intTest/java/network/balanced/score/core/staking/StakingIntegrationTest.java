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

import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.interfaces.*;
import network.balanced.score.test.ScoreIntegrationTest;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.core.staking.utils.Constant.ONE_EXA;
import static network.balanced.score.interfaces.StakingInterface.*;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class StakingIntegrationTest implements ScoreIntegrationTest {
    // keep the first wallet address here
    private final Address senderAddress = Address.fromString("hx882d4134ac6df7b4cebf75667e9762a6a2f2ff63");
    // keep the second wallet address here
    private final Address testerAddress = Address.fromString("hx2d47d4fb841322917b3f01b9460eff1bfdad499c");
    // keep the third wallet address here
    private final Address testerAddress3 = Address.fromString("hx18846d315631f1a2b1602c774abf8b2f6556b423");
    private final Address user2 = Address.fromString("hx3f01840a599da07b0f620eeae7aa9c574169a4be");
    // keep the staking address here
    private final Address stakingAddress = Address.fromString("cxee48cb7db2ac535d9530f288003384d5b6a3d93f");


    DefaultScoreClient stakingClient = DefaultScoreClient.of(System.getProperties());

    @ScoreClient
    StakingInterface stakingManagementScore = new StakingInterfaceScoreClient(stakingClient);

    Wallet tester = DefaultScoreClient.wallet("tester.", System.getProperties());
    Wallet tester2 = DefaultScoreClient.wallet("tester2.", System.getProperties());

    DefaultScoreClient clientWithTester = new DefaultScoreClient(stakingClient.endpoint(), stakingClient._nid(), tester,
            stakingClient._address());
    DefaultScoreClient clientWithTester2 = new DefaultScoreClient(stakingClient.endpoint(), stakingClient._nid(), tester2,
            stakingClient._address());

    @ScoreClient
    StakingInterface testerScore = new StakingInterfaceScoreClient(clientWithTester);



    @ScoreClient
    StakingInterface testerScore2 = new StakingInterfaceScoreClient(clientWithTester2);

    Map<String, Object> params = Map.of("_admin", stakingClient._address());

    DefaultScoreClient sicxClient = DefaultScoreClient.of("sicx.", System.getProperties(), params);
    DefaultScoreClient sicxClient2 = new DefaultScoreClient(sicxClient.endpoint(), sicxClient._nid(), tester2,
            sicxClient._address());
    DefaultScoreClient systemClient = DefaultScoreClient.of("system.", System.getProperties(), params);

    @ScoreClient
    SystemInterface systemScore = new SystemInterfaceScoreClient(systemClient);

    @ScoreClient
    SicxInterface sicxScore = new SicxInterfaceScoreClient(sicxClient);

    @ScoreClient
    SicxInterface sicxScore2 = new SicxInterfaceScoreClient(sicxClient2);

//    @BeforeEach
//    void beforeAll() {
//        scoreClient.setDemoAddress(demoClient._address());
//    }



    @Test
    void testName() {
        assertEquals("Staked ICX Manager", stakingManagementScore.name());
    }

    @Test
    void testSicxAddress() {
        stakingManagementScore.toggleStakingOn();
        stakingManagementScore.setSicxAddress(sicxClient._address());
        Address value = stakingManagementScore.getSicxAddress();
        assertEquals(sicxClient._address(), value);
    }

    @Test
    void checkTopPreps() {
        List<Address> topPrep = stakingManagementScore.getTopPreps();
        List<Address> prepList = stakingManagementScore.getPrepList();
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();

        BigInteger sum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            sum = sum.add(value);
        }

        assertEquals(100, topPrep.size());
        assertEquals(new BigInteger("0"), sum);
        assertEquals(100, prepList.size());
    }

    @Test
    void testStakeIcxByNewUser() {
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
                , null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();


        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), ONE_EXA);
                expectedNetworkDelegations.put(prep.toString(), ONE_EXA);
                expectedPrepDelegations.put(prep.toString(), ONE_EXA);
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)),
                stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)),
                sicxScore.totalSupply());
        assertEquals(userBalance.add(new BigInteger("100").multiply(ONE_EXA)),
                sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    void testSecondStakeIcx() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        // stakes 100 ICX to user2
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("200").multiply(ONE_EXA),
                user2, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(user2);

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("200").multiply(ONE_EXA)),
                stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("200").multiply(ONE_EXA)),
                sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("200").multiply(ONE_EXA)),
                sicxScore.balanceOf(user2));
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
    void delegate() {


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(20);
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


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
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        BigInteger currentTotalStake = stakingManagementScore.getTotalStake();
        assertEquals(previousTotalStake, currentTotalStake);
        assertEquals(currentTotalStake, prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void delegateToThreePreps() {

        PrepDelegations p = new PrepDelegations();
        PrepDelegations p2 = new PrepDelegations();
        PrepDelegations p3 = new PrepDelegations();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        List<Address> delegatedAddressList = stakingManagementScore.getTopPreps();
        p._address = delegatedAddressList.get(25);
        p._votes_in_per = new BigInteger("50").multiply(ONE_EXA);
        p2._address = delegatedAddressList.get(26);
        p2._votes_in_per = new BigInteger("25").multiply(ONE_EXA);
        p3._address = delegatedAddressList.get(27);
        p3._votes_in_per = new BigInteger("25").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p, p2, p3
        };

        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (contains(prep, topPreps)) {
                if (prep.toString().equals(delegatedAddressList.get(26).toString())) {

                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                } else if (prep.toString().equals(delegatedAddressList.get(27).toString())) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("27").multiply(ONE_EXA));
                } else if (prep.toString().equals(delegatedAddressList.get(25).toString())) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("52").multiply(ONE_EXA));
                    userExpectedDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("52").multiply(ONE_EXA));
                } else {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("2").multiply(ONE_EXA));

                }
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);


        assertEquals(new BigInteger("300").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);


    }

    @Test
    void delegateOutsideTopPrep() {
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
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
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        assertEquals(new BigInteger("300").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void stakeAfterDelegate() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
                , null);

        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
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
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        assertEquals(new BigInteger("400").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)), stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)), sicxScore.totalSupply());
        assertEquals(userBalance.add(new BigInteger("100").multiply(ONE_EXA)), sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

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
    void transferPreferenceToNoPreference() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        sicxScore.transfer(user2, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(new BigInteger(
                "50").multiply(ONE_EXA));
        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
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
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(user2);

        assertEquals(new BigInteger("400").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(newUserBalance,
                sicxScore.balanceOf(senderAddress));
        assertEquals(secondUserBalance.add(new BigInteger("50").multiply(ONE_EXA)),
                sicxScore.balanceOf(user2));
        checkNetworkDelegations(expectedNetworkDelegations);

    }


    @Test
    void delegateFirstThenStake(){
        PrepDelegations p = new PrepDelegations();
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScore.balanceOf(testerAddress);

        // delegates to one address
        testerScore.delegate(userDelegation);

        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(testerAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(delegatedAddress.toString(), BigInteger.ZERO);
        assertEquals(userDelegations,userExpectedDelegations );

        ((StakingInterfaceScoreClient) testerScore).stakeICX(new BigInteger("50").multiply(ONE_EXA), null
                , null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        userDelegations.clear();
        userDelegations = stakingManagementScore.getAddressDelegations(testerAddress);

        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            }
            else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(new BigInteger("525").multiply(ONE_EXA).divide(BigInteger.TEN)));
                userExpectedDelegations.put(prep.toString(), testerBalance.add(new BigInteger("50").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("54").multiply(ONE_EXA));
            }

            else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("4").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA).divide(BigInteger.TEN));}
        }

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("50").multiply(ONE_EXA)), stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("50").multiply(ONE_EXA)), sicxScore.totalSupply());
        assertEquals(testerBalance.add(new BigInteger("50")).multiply(ONE_EXA),
                sicxScore.balanceOf(testerAddress));
        checkNetworkDelegations(expectedNetworkDelegations);
    }


    @Test
    void transferPreferenceToPreference() {
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScore.balanceOf(testerAddress);
        testerBalance = testerBalance.subtract(new BigInteger("50").multiply(ONE_EXA));

        sicxScore.transfer(testerAddress, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        BigInteger newUserBalance = userBalance.subtract(new BigInteger("50").multiply(ONE_EXA));
        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations.put(prep.toString(), newUserBalance);
            }
            else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(new BigInteger("1025").multiply(ONE_EXA).divide(BigInteger.TEN)));
                user2ExpectedDelegations.put(prep.toString(), testerBalance.add(new BigInteger("50").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("1035").multiply(ONE_EXA).divide(BigInteger.TEN));
            }
            else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("25").multiply(ONE_EXA).divide(BigInteger.TEN));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(testerAddress);
        assertEquals(new BigInteger("450").multiply(ONE_EXA), prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(user2Delegations, user2ExpectedDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(newUserBalance,
                sicxScore.balanceOf(senderAddress));
        assertEquals(testerBalance.add(new BigInteger("50").multiply(ONE_EXA)),
                sicxScore.balanceOf(testerAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void transferNullToNull() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScore.balanceOf(testerAddress);
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);
        Address receiverAddress = stakingManagementScore.getTopPreps().get(43);


        ((StakingInterfaceScoreClient) testerScore2).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
                , null);

        sicxScore2.transfer(receiverAddress, new BigInteger("50").multiply(ONE_EXA), null);



        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();
        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), userBalance);
            }
            else {
                BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);
                if (prep.toString().equals(delegatedAddress.toString())) {
                    expectedPrepDelegations.put(prep.toString(), testerBalance.add(new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN)));
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("1045").multiply(ONE_EXA).divide(BigInteger.TEN));
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                }
                else if (contains(prep, topPreps)) {
                    userExpectedDelegations.put(prep.toString(), divide);
                    user2ExpectedDelegations.put(prep.toString(), divide);
                    expectedNetworkDelegations.put(prep.toString(), new BigInteger("45").multiply(ONE_EXA).divide(BigInteger.TEN));
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                }
            }
        }

        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(testerAddress3);
        Map<String, BigInteger> userDelegations2 = stakingManagementScore.getAddressDelegations(receiverAddress);
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();

        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(userDelegations2, user2ExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)), stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)), sicxScore.totalSupply());
        assertEquals(new BigInteger("50").multiply(ONE_EXA),
                sicxScore.balanceOf(receiverAddress));
        assertEquals(new BigInteger("50").multiply(ONE_EXA),
                sicxScore.balanceOf(testerAddress3));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void transferNullToPreference() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger testerBalance = sicxScore.balanceOf(testerAddress);
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);

        sicxScore2.transfer(senderAddress, new BigInteger("50").multiply(ONE_EXA), null);

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> userExpectedDelegations2 = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        BigInteger newUserBalance = userBalance.add(new BigInteger(
                "50").multiply(ONE_EXA));
        BigInteger divide = new BigInteger("5").multiply(ONE_EXA).divide(BigInteger.TEN);

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), newUserBalance);
                userExpectedDelegations2.put(prep.toString(), newUserBalance);

            }
            else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedPrepDelegations.put(prep.toString(), testerBalance.add(new BigInteger("3").multiply(ONE_EXA)));
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("1045").multiply(ONE_EXA).divide(BigInteger.TEN));
                userExpectedDelegations.put(prep.toString(), divide);
            }
            else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("45").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), divide);
            }
        }

        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(testerAddress3);
        Map<String, BigInteger> userDelegations2 = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();

        assertEquals(userDelegations, new java.util.HashMap<>());
        assertEquals(userDelegations2, userExpectedDelegations2);
        assertEquals(prepDelegations, expectedPrepDelegations);
        assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply, sicxScore.totalSupply());
        assertEquals(new BigInteger("0").multiply(ONE_EXA),
                sicxScore.balanceOf(testerAddress3));
        assertEquals(new BigInteger("150").multiply(ONE_EXA),
                sicxScore.balanceOf(senderAddress));
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void unstakePartial() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");

        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);

        sicxScore.transfer(stakingAddress, new BigInteger("100").multiply(ONE_EXA), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
                userExpectedDelegations.put(prep.toString(), new BigInteger("50").multiply(ONE_EXA));
            }
            else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("1035").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
            }
            else if (contains(prep, topPreps)) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("35").multiply(ONE_EXA).divide(BigInteger.TEN));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3").multiply(ONE_EXA));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        assertEquals(new BigInteger("450").multiply(ONE_EXA),
                stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("100").multiply(ONE_EXA), stakingManagementScore.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("100").multiply(ONE_EXA)),
                sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("100").multiply(ONE_EXA)),
                sicxScore.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("100").multiply(ONE_EXA), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals(new BigInteger("450").multiply(ONE_EXA),

                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);
    }

    @Test
    void unstakeFull() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");



        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(33);


        sicxScore.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedNetworkDelegations = new java.util.HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList) {
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("0"));
            }
            else if (prep.toString().equals(delegatedAddress.toString())) {
                expectedNetworkDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("103").multiply(ONE_EXA));
            }
            else if (contains(prep, topPreps)) {
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
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        assertEquals(previousTotalStake.subtract(new BigInteger("50").multiply(ONE_EXA)),
                stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("150").multiply(ONE_EXA), stakingManagementScore.getUnstakingAmount());
        assertEquals(previousTotalSupply.subtract(new BigInteger("50").multiply(ONE_EXA)),
                sicxScore.totalSupply());
        assertEquals(userBalance.subtract(new BigInteger("50").multiply(ONE_EXA)),
                sicxScore.balanceOf(senderAddress));


        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", userUnstakeInfo.get(1).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(1).get("from"));
        String hexValue = (String) userUnstakeInfo.get(1).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("50").multiply(ONE_EXA), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4).toString());
        assertEquals(new BigInteger("50").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        assertEquals(new BigInteger("400").multiply(ONE_EXA),
                prepDelegationsSum);
        assertEquals(userDelegations, userExpectedDelegations);
        assertEquals(prepDelegations, expectedPrepDelegations);
        checkNetworkDelegations(expectedNetworkDelegations);

    }

    @Test
    void stakeAfterUnstake() {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("10").multiply(ONE_EXA),
        null, null);

        Assertions.assertEquals(new BigInteger("140").multiply(ONE_EXA), stakingManagementScore.getUnstakingAmount());

        List<Map<String, Object>>userUnstakeInfo =stakingManagementScore.getUserUnstakeInfo(senderAddress);
        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get(0).get("sender"));
        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x","");
        Assertions.assertEquals(new BigInteger("90").multiply(ONE_EXA), new BigInteger(hexValue, 16));
        Assertions.assertEquals(new BigInteger("10").multiply(ONE_EXA), stakingManagementScore.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("10").multiply(ONE_EXA), stakingManagementScore.claimableICX(senderAddress));
        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(2));
        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(4));
        Assertions.assertEquals(new BigInteger("50").multiply(ONE_EXA),  new BigInteger(hexValue, 16));
                ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("500").multiply(ONE_EXA),
        null, null);
        Assertions.assertEquals(new BigInteger("0"), stakingManagementScore.getUnstakingAmount());
        assertEquals(previousTotalStake.add(new BigInteger("510").multiply(ONE_EXA)), stakingManagementScore.getTotalStake());
        assertEquals(previousTotalSupply.add(new BigInteger("510").multiply(ONE_EXA)), sicxScore.totalSupply());

        Assertions.assertEquals(new BigInteger("150").multiply(ONE_EXA), stakingManagementScore.totalClaimableIcx());
        Assertions.assertEquals(new BigInteger("100").multiply(ONE_EXA), stakingManagementScore.claimableICX(senderAddress));
        Assertions.assertEquals(new BigInteger("50").multiply(ONE_EXA), stakingManagementScore.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        ((StakingInterfaceScoreClient) stakingManagementScore).claimUnstakedICX(senderAddress);
        ((StakingInterfaceScoreClient) stakingManagementScore).claimUnstakedICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        Assertions.assertEquals(Context.getBalance(stakingAddress), BigInteger.ZERO);
    }


    @Test
    void beforeUpdate() throws Exception {

        // stakes ICX
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
                , null);
        // Delegation is changed
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx24791b621e1f25bbac71e2bab8294ff38294a2c6");
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        stakingManagementScore.delegate(userDelegation);
        sicxScore.transfer(user2, new BigInteger("50").multiply(ONE_EXA), null);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("2").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("15000000000000000000"), null,
                null);
        checkReadonlyFunctions();

    }

    void checkReadonlyFunctions() throws Exception {
        assertEquals(new BigInteger("73000000000000000000"), stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("27000000000000000000"), stakingManagementScore.getUnstakingAmount());
        assertEquals(sicxClient._address(), stakingManagementScore.getSicxAddress());
        assertEquals(new BigInteger("12000000000000000000"),
                stakingManagementScore.claimableICX(senderAddress));
        assertEquals(new BigInteger("3000000000000000000"),
                stakingManagementScore.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        assertEquals(new BigInteger("15000000000000000000"), stakingManagementScore.totalClaimableIcx());
        assertEquals(new BigInteger("23000000000000000000"), sicxScore.balanceOf(senderAddress));
        assertEquals(new BigInteger("50000000000000000000"), sicxScore.balanceOf(user2));

        //checks address delegation of a sender
        checkAddressDelegations(senderAddress, "hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger(
                "23000000000000000000"));
        checkFirstUserUnstakeInfo();
        checkSecondUserUnstakeInfo();
        checkUnstakeInfo();
        checkPrepDelegations("hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger("23500000000000000000"),
                new BigInteger("500000000000000000"));
    }

    void checkAddressDelegations(Address senderAddress, String prep, BigInteger delegations) {
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put(prep, delegations);
        assertEquals(userDelegations, userExpectedDelegations);
    }

    void checkPrepDelegations(String delegatedAddress, BigInteger specificDelegations, BigInteger evenlyDelegation) {
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();
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
        assertEquals(stakingManagementScore.getPrepDelegations(), expectedPrepDelegations);

    }

    void checkFirstUserUnstakeInfo() throws Exception {
        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(senderAddress);

        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("sender"));
        assertEquals(senderAddress.toString(), userUnstakeInfo.get(0).get("from"));
        String hexValue = (String) userUnstakeInfo.get(0).get("amount");
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10").multiply(ONE_EXA), new BigInteger(hexValue, 16));
    }

    void checkSecondUserUnstakeInfo() throws Exception {
        List<Map<String, Object>> userUnstakeInfo = stakingManagementScore.getUserUnstakeInfo(Address.fromString(
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
        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        assertEquals(senderAddress.toString(), firstItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", firstItem.get(4).toString());

        String value = (String) firstItem.get(1);
        value = value.replace("0x", "");
        assertEquals(new BigInteger("7000000000000000000"), new BigInteger(value, 16));
        List<Object> secondItem = unstakeInfo.get(1);
        assertEquals(senderAddress.toString(), secondItem.get(2).toString());
        assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664", secondItem.get(4).toString());
        String hexVal = (String) secondItem.get(1) ;
        hexVal = hexVal.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexVal, 16));
        List<Object> thirdtItem = unstakeInfo.get(2);
        assertEquals(senderAddress.toString(), thirdtItem.get(2).toString());
        assertEquals(senderAddress.toString(), thirdtItem.get(4).toString());
        String hexValue = (String) thirdtItem.get(1);
        hexValue = hexValue.replace("0x", "");
        assertEquals(new BigInteger("10000000000000000000"), new BigInteger(hexValue, 16));
    }

    @Test
    void test() throws Exception {
        checkReadonlyFunctions();
    }

    @Test
    void afterUpdate() throws Exception {
        checkReadonlyFunctions();


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        Address delegatedAddress = stakingManagementScore.getTopPreps().get(77);
//         stakes ICX
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("100").multiply(ONE_EXA), null
                , null);
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100").multiply(ONE_EXA)), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("100").multiply(ONE_EXA)), sicxScore.totalSupply());
        // Delegation is changed
        PrepDelegations p = new PrepDelegations();
        p._address = delegatedAddress;
        p._votes_in_per = new BigInteger("100").multiply(ONE_EXA);
        PrepDelegations[] userDelegation = new PrepDelegations[]{p};
        stakingManagementScore.delegate(userDelegation);
        sicxScore.transfer(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"), new BigInteger(
                "50").multiply(ONE_EXA), null);
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("2").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        data.clear();
        data.put("method", "unstake");
        sicxScore.transfer(stakingAddress, new BigInteger("10").multiply(ONE_EXA), data.toString().getBytes());
        ((StakingInterfaceScoreClient) stakingManagementScore).stakeICX(new BigInteger("15").multiply(ONE_EXA), null,
                null);
        assertEquals(new BigInteger("146").multiply(ONE_EXA), stakingManagementScore.getTotalStake());
        assertEquals(new BigInteger("27").multiply(ONE_EXA), stakingManagementScore.getUnstakingAmount());
        assertEquals(sicxClient._address(), stakingManagementScore.getSicxAddress());
        assertEquals(new BigInteger("34").multiply(ONE_EXA),
                stakingManagementScore.claimableICX(senderAddress));
        assertEquals(new BigInteger("23").multiply(ONE_EXA),
                stakingManagementScore.claimableICX(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));
        assertEquals(new BigInteger("57").multiply(ONE_EXA), stakingManagementScore.totalClaimableIcx());
        assertEquals(new BigInteger("46").multiply(ONE_EXA), sicxScore.balanceOf(senderAddress));
        assertEquals(new BigInteger("50").multiply(ONE_EXA), sicxScore.balanceOf(user2));
        assertEquals(new BigInteger("50").multiply(ONE_EXA), sicxScore.balanceOf(Address.fromString(
                "hx8119b3eebeb9f857efb3b135275ac3775cbc6664")));

        checkAddressDelegations(senderAddress, delegatedAddress.toString(), new BigInteger(
                "46000000000000000000"));
        checkFirstUserUnstakeInfo();
        checkSecondUserUnstakeInfo();
//        checkUnstakeInfo();
        checkPrepDelegations(delegatedAddress.toString(), new BigInteger("47000000000000000000"),
                new BigInteger("1000000000000000000"));
    }

}
