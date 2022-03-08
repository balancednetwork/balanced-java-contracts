package score.test.staking;

import ai.ibriz.core.score.interfaces.*;
import ai.ibriz.libs.test.ScoreIntegrationTest;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.core.utils.Constant;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import score.Address;
import score.Context;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;




public class StakingIntegrationTest implements ScoreIntegrationTest {
    private Address senderAddress = Address.fromString("hx882d4134ac6df7b4cebf75667e9762a6a2f2ff63");
    private Address user2 = Address.fromString("hx3f01840a599da07b0f620eeae7aa9c574169a4be");
    private Address stakingAddress = Address.fromString("cxd7ce24c5e7be2efef1724967e5b71757a49ba7b7");

    DefaultScoreClient stakingClient = DefaultScoreClient.of(System.getProperties());

//    DefaultScoreClient demoClient=DefaultScoreClient.of("demo.",System.getProperties());



    @ScoreClient
    StakingInterface stakingManagementScore = new StakingInterfaceScoreClient(stakingClient);


    Map<String, Object> params = Map.of("_admin",stakingClient._address());

    DefaultScoreClient sicxClient = DefaultScoreClient.of("sicx.", System.getProperties(), params);
    DefaultScoreClient systemClient = DefaultScoreClient.of("system.", System.getProperties(), params);

    @ScoreClient
    SystemInterface systemScore = new SystemInterfaceScoreClient(systemClient);


    @ScoreClient
    SicxInterface sicxScore = new SicxInterfaceScoreClient(sicxClient);

//    @ScoreClient(suffix = "Client")
//    Demo scoreDemo = new DemoClient(demoClient);


//    @BeforeEach
//    void beforeAll() {
//        scoreClient.setDemoAddress(demoClient._address());
//    }


    @Test
    void testName() {
        Assertions.assertEquals("TEST Staked ICX Manager", stakingManagementScore.name());
    }

    @Test
    void testSicxAddress() {
        stakingManagementScore.toggleStakingOn();
        stakingManagementScore.setSicxAddress(sicxClient._address());
        Address value = stakingManagementScore.getSicxAddress();
        Assertions.assertEquals(sicxClient._address(), value);

//        System.out.println(systemScore.getIISSInfo());
//        Address stakingAddress = Address.fromString("cx0b9cc301f133f55156a09b1f63f51fe72c92b0da");
//        System.out.println(systemScore.getStake(stakingAddress));
//        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
//        Map<String, BigInteger> networkDelegations = new HashMap<>();
//        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");
//        System.out.println(delegationList);
//
//        for (Map<String, Object> del : delegationList){
//            String hexValue = del.get("value").toString();
//            hexValue = hexValue.replace("0x","");
//            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
//        }

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

        Assertions.assertEquals(100,topPrep.size());
        Assertions.assertEquals(new BigInteger("0"),sum);
        Assertions.assertEquals(100,prepList.size());
    }

    @Test
    void testStakeIcx() throws Exception {

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        // stakes 50 ICX by user1
        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();


        for (Address prep : prepList){
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), Constant.DENOMINATOR);
                expectedPrepDelegations.put(prep.toString(), Constant.DENOMINATOR);
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("100000000000000000000")), sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.add(new BigInteger("100000000000000000000")), sicxScore.balanceOf(senderAddress));
    }

    @Test
    void testSecondStakeIcx() throws Exception {
        BigInteger previousTotalStake = new BigInteger("100000000000000000000");
        BigInteger previousTotalSupply = new BigInteger("100000000000000000000");
        BigInteger userBalance = new BigInteger("100000000000000000000");
        BigInteger secondUserBalance = new BigInteger("0");


        // stakes 100 ICX to user2
        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("200000000000000000000"), user2, null);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList){
            if (contains(prep, topPreps)) {
                userExpectedDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(user2);

        Assertions.assertEquals(previousTotalStake.add(new BigInteger("200000000000000000000")), prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        System.out.println("here");
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("200000000000000000000")), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("200000000000000000000")), sicxScore.totalSupply());
        Assertions.assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
        Assertions.assertEquals(secondUserBalance.add(new BigInteger("200000000000000000000")), sicxScore.balanceOf(user2));
    }


    public boolean contains(Address target, List<Address> addresses) {
        for(Address address : addresses) {
            if (address.equals(target)){
                return true;
            }
        }
        return false;
    }

    @Test
    void delegate() throws Exception {


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx24791b621e1f25bbac71e2bab8294ff38294a2c6");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p
        };
        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList){
            if (contains(prep, topPreps)) {
                if (prep.toString().equals("hx24791b621e1f25bbac71e2bab8294ff38294a2c6")){
                    expectedPrepDelegations.put(prep.toString(),  new BigInteger("102000000000000000000"));
                }
                else{
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));

                }
            }
        }

        userExpectedDelegations.put("hx24791b621e1f25bbac71e2bab8294ff38294a2c6", new BigInteger("100000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

//        Map<String, Object> delegations = systemScore.getDelegation(stakingAddress);
//        Map<String, BigInteger> networkDelegations = new HashMap<>();
//        List<Map<String, Object>> delegationList = (List<Map<String, Object>>) delegations.get("delegations");
//        System.out.println(delegationList);
//
//        for (Map<String, Object> del : delegationList){
//            String hexValue = del.get("value").toString();
//            hexValue = hexValue.replace("0x","");
//            networkDelegations.put(del.get("address").toString(), new BigInteger(hexValue, 16));
//        }

        Assertions.assertEquals(previousTotalStake, prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
//        Assertions.assertTrue(prepDelegations.equals(networkDelegations));
        Assertions.assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply, sicxScore.totalSupply());
        Assertions.assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
    }

    @Test
    void delegateToThreePreps() throws Exception {

        PrepDelegations p = new PrepDelegations();
        PrepDelegations p2=new PrepDelegations();
        PrepDelegations p3=new PrepDelegations();
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        p._address = Address.fromString("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff");
        p._votes_in_per = new BigInteger("50000000000000000000");
        p2._address=Address.fromString("hx3a0a9137344fdb552a146033401a52f27272c362");
        p2._votes_in_per=new BigInteger("25000000000000000000");
        p3._address=Address.fromString("hx38f35eff5e5516b48a713fe3c8031c94124191f0");
        p3._votes_in_per=new BigInteger("25000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p,p2,p3
        };

                // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList){
            if (contains(prep, topPreps)) {
                if (prep.toString().equals("hx3a0a9137344fdb552a146033401a52f27272c362")){
                    expectedPrepDelegations.put(prep.toString(),  new BigInteger("27000000000000000000"));
                }
                else if ( prep.toString().equals("hx38f35eff5e5516b48a713fe3c8031c94124191f0")){
                    expectedPrepDelegations.put(prep.toString(),  new BigInteger("27000000000000000000"));
                }
                else if (prep.toString().equals("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff")){
                    expectedPrepDelegations.put(prep.toString(),  new BigInteger("52000000000000000000"));
                }
                else{
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));

                }
            }
        }

        userExpectedDelegations.put("hx3a0a9137344fdb552a146033401a52f27272c362", new BigInteger("25000000000000000000"));
        userExpectedDelegations.put("hx38f35eff5e5516b48a713fe3c8031c94124191f0", new BigInteger("25000000000000000000"));
        userExpectedDelegations.put("hx3c7955f918f07df3b30c45b20f829eb8b4c8f6ff", new BigInteger("50000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);


        Assertions.assertEquals(previousTotalStake, prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        Assertions.assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply, sicxScore.totalSupply());
        Assertions.assertEquals(userBalance, sicxScore.balanceOf(senderAddress));


    }

    @Test
    void delegateOutsideTopPrep() throws Exception {
        PrepDelegations p = new PrepDelegations();
//        PrepDelegations p2=new PrepDelegations();
        p._address = Address.fromString("hx051e14eb7d2e04fae723cd610c153742778ad5f7");
        p._votes_in_per = new BigInteger("100000000000000000000");
        PrepDelegations[] userDelegation = new PrepDelegations[]{
                p
        };
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        // delegates to one address
        stakingManagementScore.delegate(userDelegation);

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  new BigInteger("100000000000000000000"));
            }
            if (contains(prep, topPreps)) {
                    expectedPrepDelegations.put(prep.toString(), new BigInteger("2000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("100000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);

        Assertions.assertEquals(previousTotalStake, prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        Assertions.assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply, sicxScore.totalSupply());
        Assertions.assertEquals(userBalance, sicxScore.balanceOf(senderAddress));
    }

    @Test
    void transferToExistingUser() throws Exception {


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);

        sicxScore.transfer(user2, new BigInteger("50000000000000000000"), null );


        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  userBalance.subtract(new BigInteger("50000000000000000000")));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
                user2ExpectedDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", userBalance.subtract(new BigInteger("50000000000000000000")));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(user2);

        Assertions.assertEquals(previousTotalStake, prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        Assertions.assertTrue(user2Delegations.equals(user2ExpectedDelegations));
        Assertions.assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply, sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")), sicxScore.balanceOf(senderAddress));
        Assertions.assertEquals(secondUserBalance.add(new BigInteger("50000000000000000000")), sicxScore.balanceOf(user2));


    }

    @Test
    void transferToNewUser() throws Exception {
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        BigInteger secondUserBalance = sicxScore.balanceOf(user2);
        Address newUser = Address.fromString("hxa88f303893fa19e4d8031dd88f6b8aa993997150");
        BigInteger newUserBalance = sicxScore.balanceOf(newUser);

        sicxScore.transfer(newUser, new BigInteger("50000000000000000000"), null );


        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> user2ExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> newUserExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();

        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  userBalance.subtract(new BigInteger("50000000000000000000")));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
                user2ExpectedDelegations.put(prep.toString(), new BigInteger("2500000000000000000"));
                newUserExpectedDelegations.put(prep.toString(), new BigInteger("500000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", userBalance.subtract(new BigInteger("50000000000000000000")));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Map<String, BigInteger> user2Delegations = stakingManagementScore.getAddressDelegations(user2);
        Map<String, BigInteger> newUserDelegations = stakingManagementScore.getAddressDelegations(newUser);

        Assertions.assertEquals(previousTotalStake, prepDelegationsSum);
        Assertions.assertEquals(previousTotalStake, stakingManagementScore.getTotalStake());
        Assertions.assertEquals(previousTotalSupply, sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")), sicxScore.balanceOf(senderAddress));
        Assertions.assertEquals(secondUserBalance, sicxScore.balanceOf(user2));
        Assertions.assertEquals(newUserBalance.add(new BigInteger("50000000000000000000")), sicxScore.balanceOf(newUser));
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
        Assertions.assertTrue(user2Delegations.equals(user2ExpectedDelegations));
        Assertions.assertTrue(newUserDelegations.equals(newUserExpectedDelegations));

    }


    @Test
    void unstakeHalf() throws Exception {
//        stakingManagementScore.toggleStakingOn();
        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null, null);
//        stakingManagementScore.toggleStakingOn();
        JSONObject data = new JSONObject();
        data.put("method", "unstake");


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        sicxScore.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  new BigInteger("50000000000000000000"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("50000000000000000000"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Assertions.assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(new BigInteger("50000000000000000000"), stakingManagementScore.getUnstakingAmount());
//        Assertions.assertEquals(new BigInteger("50000000000000000000"), stakingManagementScore.getUnstakingAmount());
        Assertions.assertEquals(previousTotalSupply.subtract(new BigInteger("50000000000000000000")), sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")), sicxScore.balanceOf(senderAddress));


        Map<String, Object> userUnstakeInfo =stakingManagementScore.getUserUnstakeInfo(senderAddress);

        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("sender"));
        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("from"));
        String hexValue = (String) userUnstakeInfo.get("amount");
            hexValue = hexValue.replace("0x","");
        Assertions.assertEquals(new BigInteger("50000000000000000000"), new BigInteger(hexValue, 16));

        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(0);
        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(2));
        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(4));
        Assertions.assertEquals(new BigInteger("50000000000000000000"),  new BigInteger(hexValue, 16));
        Assertions.assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")), prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
    }

    @Test
    void unstakeFull() throws Exception {
        JSONObject data = new JSONObject();
        data.put("method", "unstake");
        data.put("user", "hx8119b3eebeb9f857efb3b135275ac3775cbc6664");


        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);

        sicxScore.transfer(stakingAddress, new BigInteger("50000000000000000000"), data.toString().getBytes());

        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  new BigInteger("0"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }

        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("0"));

        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Assertions.assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(new BigInteger("100000000000000000000"), stakingManagementScore.getUnstakingAmount());
        Assertions.assertEquals(previousTotalSupply.subtract(new BigInteger("50000000000000000000")), sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.subtract(new BigInteger("50000000000000000000")), sicxScore.balanceOf(senderAddress));


        Map<String, Object> userUnstakeInfo =stakingManagementScore.getUserUnstakeInfo(Address.fromString("hx8119b3eebeb9f857efb3b135275ac3775cbc6664"));
        Assertions.assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664",  userUnstakeInfo.get("sender"));
        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("from"));
        String hexValue = (String) userUnstakeInfo.get("amount");
        hexValue = hexValue.replace("0x","");
        Assertions.assertEquals(new BigInteger("50000000000000000000"), new BigInteger(hexValue, 16));
        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
        List<Object> firstItem = unstakeInfo.get(1);
        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(2));
        Assertions.assertEquals("hx8119b3eebeb9f857efb3b135275ac3775cbc6664",  firstItem.get(4));
        Assertions.assertEquals(new BigInteger("50000000000000000000"),  new BigInteger(hexValue, 16));
        Assertions.assertEquals(previousTotalStake.subtract(new BigInteger("50000000000000000000")), prepDelegationsSum);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
    }

    @Test
    void stakeAfterUnstake() throws Exception {
//        BigInteger previousBalance = Context.getBalance(senderAddress);
        BigInteger previousTotalStake = stakingManagementScore.getTotalStake();
        BigInteger previousTotalSupply = sicxScore.totalSupply();
        BigInteger userBalance = sicxScore.balanceOf(senderAddress);
        ((StakingInterfaceScoreClient)stakingManagementScore).stakeICX(new BigInteger("100000000000000000000"), null, null);
//        Assertions.assertEquals(previousBalance.add(new BigInteger("1000000000000000000")), Context.getBalance(senderAddress));

        Map<String, BigInteger> userExpectedDelegations = new HashMap<>();
        userExpectedDelegations.put("hx051e14eb7d2e04fae723cd610c153742778ad5f7", new BigInteger("101000000000000000000"));
        Map<String, BigInteger> userDelegations = stakingManagementScore.getAddressDelegations(senderAddress);
        Assertions.assertTrue(userDelegations.equals(userExpectedDelegations));






        // get prep delegations
        Map<String, BigInteger> prepDelegations = stakingManagementScore.getPrepDelegations();
        Map<String, BigInteger> expectedPrepDelegations = new HashMap<>();
        List<Address> prepList = stakingManagementScore.getPrepList();
        List<Address> topPreps = stakingManagementScore.getTopPreps();


        for (Address prep : prepList){
            if (prep.toString().equals("hx051e14eb7d2e04fae723cd610c153742778ad5f7")){
                expectedPrepDelegations.put(prep.toString(),  new BigInteger("101000000000000000000"));
            }
            if (contains(prep, topPreps)) {
                expectedPrepDelegations.put(prep.toString(), new BigInteger("3000000000000000000"));
            }
        }


        BigInteger prepDelegationsSum = new BigInteger("0");
        for (BigInteger value : prepDelegations.values()) {
            prepDelegationsSum = prepDelegationsSum.add(value);
        }
        // get address delegations of a user
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), stakingManagementScore.getTotalStake());
        Assertions.assertEquals(new BigInteger("0"), stakingManagementScore.getUnstakingAmount());
        Assertions.assertEquals(previousTotalSupply.add(new BigInteger("100000000000000000000")), sicxScore.totalSupply());
        Assertions.assertEquals(userBalance.add(new BigInteger("101000000000000000000")), sicxScore.balanceOf(senderAddress));


//        Map<String, Object> userUnstakeInfo =stakingManagementScore.getUserUnstakeInfo(senderAddress);
//        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("sender"));
//        Assertions.assertEquals(senderAddress.toString(),  userUnstakeInfo.get("from"));
//        String hexValue = (String) userUnstakeInfo.get("amount");
//        hexValue = hexValue.replace("0x","");
//        Assertions.assertEquals(new BigInteger("49000000000000000000"), new BigInteger(hexValue, 16));
//        List<List<Object>> unstakeInfo = stakingManagementScore.getUnstakeInfo();
//        List<Object> firstItem = unstakeInfo.get(0);
//        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(2));
//        Assertions.assertEquals(senderAddress.toString(),  firstItem.get(4));
//        Assertions.assertEquals(new BigInteger("49000000000000000000"),  new BigInteger(hexValue, 16));
        Assertions.assertEquals(previousTotalStake.add(new BigInteger("100000000000000000000")), prepDelegationsSum);
        Assertions.assertTrue(prepDelegations.equals(expectedPrepDelegations));
    }




}
