package network.balanced.score.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.iconloop.score.test.Score;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Request;
import foundation.icon.icx.SignedTransaction;
import foundation.icon.icx.Transaction;
import foundation.icon.icx.TransactionBuilder;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.jsonrpc.JsonrpcClient;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import score.Address;
import score.annotation.External;
import score.annotation.Optional;


/**
 * to run this integration test you might need to create 3 wallets by:
 * 
 * goloop ks gen -o governance-wallet.json
 * goloop ks gen -o user-provisioning-wallet.json
 * goloop ks gen -o user-receiver-wallet.json
 * 
 * and fill gradle.properties at root level with 
 * 
 * 
#godWallet is the one from the local goloop node 
score-test.url=http://localhost:9082/api/v3
score-test.nid=0x3
score-test.keystoreName=<wallet_base_path>/godWallet.json
score-test.keystorePass=gochain

score-test.governance.url=http://localhost:9082/api/v3
score-test.governance.nid=0x3
score-test.governance.keystoreName=<wallet_base_path>/governance-wallet.json
score-test.governance.keystorePass=gochain

score-test.user.provisioning.url=http://localhost:9082/api/v3
score-test.user.provisioning.nid=0x3
score-test.user.provisioning.keystoreName=<wallet_base_path>/user-provisioning-wallet.json
score-test.user.provisioning.keystorePass=gochain

score-test.user.receiver.url=http://localhost:9082/api/v3
score-test.user.receiver.nid=0x3
score-test.user.receiver.keystoreName=<wallet_base_path>/user-receiver-wallet.json
score-test.user.receiver.keystorePass=gochain


 * and start your local goloop node
 *  
 * and then exec:

 * balanced-java-contracts$ ./gradlew clean build optimizedJar
 * balanced-java-contracts$ ./gradlew intTest
 */
@Tag("integration")
public class BalancedTokenIT {

	private static Wallet governanceWallet = DefaultScoreClient.wallet("governance.", System.getProperties());
	private static Wallet userProvisioningWallet = DefaultScoreClient.wallet("user.provisioning.", System.getProperties());
	private static Wallet userReceiverWallet = DefaultScoreClient.wallet("user.receiver.", System.getProperties());;

    DefaultScoreClient ownerBalancedTokenClient = DefaultScoreClient.of(System.getProperties());

    @ScoreClient
    BalanceTokenInterface ownerBalancedTokenScore = new BalanceTokenInterfaceScoreClient(ownerBalancedTokenClient);

    DefaultScoreClient governanceBalancedTokenClient = new DefaultScoreClient(ownerBalancedTokenClient.endpoint(), ownerBalancedTokenClient._nid(), governanceWallet, ownerBalancedTokenClient._address());

    @ScoreClient
    BalanceTokenInterface governanceBalancedTokenScore = new BalanceTokenInterfaceScoreClient(governanceBalancedTokenClient);

    DefaultScoreClient userProvisioningBalancedTokenClient = new DefaultScoreClient(ownerBalancedTokenClient.endpoint(), ownerBalancedTokenClient._nid(), userProvisioningWallet, ownerBalancedTokenClient._address());

    @ScoreClient
    BalanceTokenInterface userProvisioningBalancedTokenScore = new BalanceTokenInterfaceScoreClient(userProvisioningBalancedTokenClient);


    DefaultScoreClient userReceiverBalancedTokenClient = new DefaultScoreClient(ownerBalancedTokenClient.endpoint(), ownerBalancedTokenClient._nid(), userReceiverWallet, ownerBalancedTokenClient._address());

    @ScoreClient
    BalanceTokenInterface userReceiverBalancedTokenScore = new BalanceTokenInterfaceScoreClient(userReceiverBalancedTokenClient);

    @BeforeAll
    public static void init() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
    	userReceiverWallet  = KeyWallet.create();
    }

    @Test
    void ShouldAUserMintAndTransferAndMakeStake() throws IOException {

    	//owner of contract is setting governance addr
    	score.Address governanceAddress = score.Address.fromString(governanceWallet.getAddress().toString());
    	ownerBalancedTokenScore.setGovernance(governanceAddress);

    	//sending some ICX to have funds to exec operations
    	BigInteger amountToTransfer = BigInteger.TEN.multiply(BigInteger.TEN).multiply(BigInteger.TEN.pow(18));

    	DefaultScoreClient.transfer(
    			ownerBalancedTokenClient,
    			BigInteger.valueOf(3l), 
    			ownerBalancedTokenClient._wallet(),
    			new BigInteger("1000000"), 
    			foundation.icon.jsonrpc.Address.of(governanceWallet), 
    			amountToTransfer, 
    			null,
    			15000l);

    	DefaultScoreClient.transfer(
    			ownerBalancedTokenClient,
    			BigInteger.valueOf(3l), 
    			ownerBalancedTokenClient._wallet(),
    			new BigInteger("1000000"), 
    			foundation.icon.jsonrpc.Address.of(userProvisioningWallet), 
    			amountToTransfer, 
    			null,
    			15000l);

    	DefaultScoreClient.transfer(
    			ownerBalancedTokenClient,
    			BigInteger.valueOf(3l), 
    			ownerBalancedTokenClient._wallet(),
    			new BigInteger("1000000"), 
    			foundation.icon.jsonrpc.Address.of(userReceiverWallet), 
    			amountToTransfer, 
    			null,
    			15000l);

        //governance addr setting admin = contract owner
        governanceBalancedTokenScore.setAdmin(score.Address.fromString(ownerBalancedTokenClient._wallet().getAddress().toString()));

        //mint 60x10^18 tokens to user provisioning
        score.Address userAddressProvisioning = score.Address.fromString( userProvisioningWallet.getAddress().toString() );
        
        BigInteger amountToMint = BigInteger.TEN
        		.multiply(BigInteger.TWO.add(BigInteger.ONE).multiply(BigInteger.TWO))
        		.multiply(BigInteger.TEN.pow(18));
        ownerBalancedTokenScore.mintTo(userAddressProvisioning, amountToMint, "first mint".getBytes());

        governanceBalancedTokenScore.toggleStakingEnabled();

        //transferring 30x10^18 tokens from provisioning user to receiver user 
        BigInteger amountToTransferToReceiver = amountToMint.divide(BigInteger.TWO);
        score.Address userReceiverAddress = score.Address.fromString( userReceiverWallet.getAddress().toString() );
        userProvisioningBalancedTokenScore.transfer(userReceiverAddress, amountToTransferToReceiver, "mole".getBytes());

        BigInteger amountToStake = amountToTransferToReceiver.divide(BigInteger.TWO);

        //user provisioning stake 15X10^18 tokens
        userProvisioningBalancedTokenScore.stake(amountToStake);

        //user receiver stake 15X10^18 tokens
        userReceiverBalancedTokenScore.stake(amountToStake);

        BigInteger totalBalance = ownerBalancedTokenScore.totalStakedBalance();
        assertEquals( amountToStake.multiply(BigInteger.TWO) ,totalBalance);

    }
}

interface BalanceTokenInterface{

	@External
    void setGovernance(Address _address);

    @External
    void setAdmin(Address _admin);

    @External
    void mintTo(Address _account, BigInteger _amount, @Optional byte[] _data);

    @External
    void transfer(Address _to, BigInteger _value, @Optional byte[] _data);

    @External
    void toggleStakingEnabled();

    @External
    void stake(BigInteger _value);

    @External(readonly=true)
    BigInteger totalStakedBalance();

}