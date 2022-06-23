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

package network.balanced.score.token;

import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.Wallet;
import foundation.icon.score.client.DefaultScoreClient;
import foundation.icon.score.client.ScoreClient;
import network.balanced.score.lib.interfaces.BalancedToken;
import network.balanced.score.lib.interfaces.BalancedTokenScoreClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * to run this integration test you might need to create 3 wallets by:
 * <p>
 * goloop ks gen -o governance-wallet.json
 * goloop ks gen -o user-provisioning-wallet.json
 * goloop ks gen -o user-receiver-wallet.json
 * <p>
 * and fill gradle.properties at root level with
 * <p>
 * <p>
 * #godWallet is the one from the local goloop node
 * score-test.url=http://localhost:9082/api/v3
 * score-test.nid=0x3
 * score-test.keystoreName=<wallet_base_path>/godWallet.json
 * score-test.keystorePass=gochain
 * <p>
 * score-test.governance.url=http://localhost:9082/api/v3
 * score-test.governance.nid=0x3
 * score-test.governance.keystoreName=<wallet_base_path>/governance-wallet.json
 * score-test.governance.keystorePass=gochain
 * <p>
 * score-test.user.provisioning.url=http://localhost:9082/api/v3
 * score-test.user.provisioning.nid=0x3
 * score-test.user.provisioning.keystoreName=<wallet_base_path>/user-provisioning-wallet.json
 * score-test.user.provisioning.keystorePass=gochain
 * <p>
 * score-test.user.receiver.url=http://localhost:9082/api/v3
 * score-test.user.receiver.nid=0x3
 * score-test.user.receiver.keystoreName=<wallet_base_path>/user-receiver-wallet.json
 * score-test.user.receiver.keystorePass=gochain
 * and start your local goloop node
 * <p>
 * and then exec:
 * balanced-java-contracts$ ./gradlew clean build optimizedJar
 * balanced-java-contracts$ ./gradlew intTest
 */
@Tag("integration")
class BalancedTokenImplIntTest {

	private static final Wallet governanceWallet = DefaultScoreClient.wallet("governance.", System.getProperties());
	private static final Wallet userProvisioningWallet = DefaultScoreClient.wallet("user.provisioning.",
			System.getProperties());
	private static Wallet userReceiverWallet = DefaultScoreClient.wallet("user.receiver.", System.getProperties());

	private final DefaultScoreClient ownerBalancedTokenClient = DefaultScoreClient.of(System.getProperties());

	@ScoreClient
	private final BalancedToken ownerBalancedTokenScore = new BalancedTokenScoreClient(ownerBalancedTokenClient);

	private final DefaultScoreClient governanceBalancedTokenClient =
			new DefaultScoreClient(ownerBalancedTokenClient.endpoint(), ownerBalancedTokenClient._nid(),
					governanceWallet, ownerBalancedTokenClient._address());
	@ScoreClient
	private final BalancedToken governanceBalancedTokenScore =
			new BalancedTokenScoreClient(governanceBalancedTokenClient);

	private final DefaultScoreClient userProvisioningBalancedTokenClient =
			new DefaultScoreClient(ownerBalancedTokenClient.endpoint(), ownerBalancedTokenClient._nid(),
					userProvisioningWallet, ownerBalancedTokenClient._address());
	@ScoreClient
	private final BalancedToken userProvisioningBalancedTokenScore =
			new BalancedTokenScoreClient(userProvisioningBalancedTokenClient);

	private final DefaultScoreClient userReceiverBalancedTokenClient =
			new DefaultScoreClient(ownerBalancedTokenClient.endpoint(),
					ownerBalancedTokenClient._nid(), userReceiverWallet, ownerBalancedTokenClient._address());



	@ScoreClient
	private final BalancedToken userReceiverBalancedTokenScore =
			new BalancedTokenScoreClient(userReceiverBalancedTokenClient);

	@BeforeAll
	static void init() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
			NoSuchProviderException {
		userReceiverWallet = KeyWallet.create();
	}

	@Test
	void ShouldAUserMintAndTransferAndMakeStake() {

		//owner of contract is setting governance addr
		score.Address governanceAddress = score.Address.fromString(governanceWallet.getAddress().toString());
		ownerBalancedTokenScore.setGovernance(governanceAddress);

		//sending some ICX to have funds to exec operations
		BigInteger amountToTransfer = BigInteger.TEN.multiply(BigInteger.TEN).multiply(BigInteger.TEN.pow(18));

		DefaultScoreClient.transfer(
				ownerBalancedTokenClient,
				BigInteger.valueOf(3L),
				ownerBalancedTokenClient._wallet(),
				new BigInteger("1000000"),
				foundation.icon.jsonrpc.Address.of(governanceWallet),
				amountToTransfer,
				null,
				15000L);

		DefaultScoreClient.transfer(
				ownerBalancedTokenClient,
				BigInteger.valueOf(3L),
				ownerBalancedTokenClient._wallet(),
				new BigInteger("1000000"),
				foundation.icon.jsonrpc.Address.of(userProvisioningWallet),
				amountToTransfer,
				null,
				15000L);

		DefaultScoreClient.transfer(
				ownerBalancedTokenClient,
				BigInteger.valueOf(3L),
				ownerBalancedTokenClient._wallet(),
				new BigInteger("1000000"),
				foundation.icon.jsonrpc.Address.of(userReceiverWallet),
				amountToTransfer,
				null,
				15000L);

		//governance addr setting admin = contract owner
		governanceBalancedTokenScore.setAdmin(score.Address.fromString(ownerBalancedTokenClient._wallet().getAddress().toString()));

		//mint 60x10^18 tokens to user provisioning
		score.Address userAddressProvisioning =
				score.Address.fromString(userProvisioningWallet.getAddress().toString());

		BigInteger amountToMint = BigInteger.TEN
				.multiply(BigInteger.TWO.add(BigInteger.ONE).multiply(BigInteger.TWO))
				.multiply(BigInteger.TEN.pow(18));
		ownerBalancedTokenScore.mintTo(userAddressProvisioning, amountToMint, "first mint".getBytes());

		governanceBalancedTokenScore.toggleStakingEnabled();

		//transferring 30x10^18 tokens from provisioning user to receiver user
		BigInteger amountToTransferToReceiver = amountToMint.divide(BigInteger.TWO);
		score.Address userReceiverAddress = score.Address.fromString(userReceiverWallet.getAddress().toString());
		userProvisioningBalancedTokenScore.transfer(userReceiverAddress, amountToTransferToReceiver,
				"mole".getBytes());

		BigInteger amountToStake = amountToTransferToReceiver.divide(BigInteger.TWO);

		//user provisioning stake 15X10^18 tokens
		userProvisioningBalancedTokenScore.stake(amountToStake);

		//user receiver stake 15X10^18 tokens
		userReceiverBalancedTokenScore.stake(amountToStake);

		BigInteger totalBalance = ownerBalancedTokenScore.totalStakedBalance();
		assertEquals(amountToStake.multiply(BigInteger.TWO), totalBalance);

	}
}

