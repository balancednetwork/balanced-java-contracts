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

package network.balanced.gradle.plugin

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import foundation.icon.icx.Wallet
import foundation.icon.jsonrpc.Address
import foundation.icon.jsonrpc.JsonrpcClient
import foundation.icon.score.client.DefaultScoreClient
import network.balanced.gradle.plugin.utils.NameMapping
import network.balanced.gradle.plugin.utils.Network
import network.balanced.gradle.plugin.utils.Score
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DeployBalancedContracts extends DefaultTask {

    private final Property<String> env
    private final Property<String> keystore
    private final Property<String> password
    private final Property<String> configFile

    private Wallet wallet
    private Network network
    private DefaultICONClient client

    DeployBalancedContracts() {
        super()
        ObjectFactory objectFactory = getProject().getObjects()
        this.env = objectFactory.property(String.class)
        this.keystore = objectFactory.property(String.class)
        this.password = objectFactory.property(String.class)
        this.configFile = objectFactory.property(String.class)
    }

    @Input
    Property<String> getKeystore() {
        return keystore
    }

    @Input
    Property<String> getPassword() {
        return password
    }

    @Input
    Property<String> getEnv() {
        return env
    }


    @Input
    Property<String> getConfigFile() {
        return configFile
    }

    @TaskAction
    void deployContracts() throws Exception {

        List<Score> scores = readSCOREs()

        this.network = Network.getNetwork(this.env.get())

        client = new DefaultICONClient(this.network)

        this.wallet = DefaultScoreClient.wallet(this.keystore.get(), this.password.get())
        Map<String, Address> addresses = ["owner": new Address(wallet.getAddress().toString())]
        logger.lifecycle('deploying contracts...')

        deployPrep()

        for (Score score : scores) {
            if (score.getPath() == null) {
                String module = NameMapping.valueOf(score.getName())
                score.path = project.tasks.getByPath(":$module:optimizedJar").outputJarName
            }
            logger.lifecycle("deploying contract $score.name :: $score.path")
            Map<String, String> addressParams = score.getAddressParams()

            for (Map.Entry<String, String> entry : addressParams.entrySet()) {
                String key = entry.getKey()
                String value = entry.getValue()
                score.addParams(key, addresses.get(value))
            }
            Address address = score.getAddress()
            if (address == null) {
                address = deploy(score)
            } else {
                update(score)
            }
            addresses.put(score.getName(), address)
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")
        LocalDateTime now = LocalDateTime.now()
        String fileName = ".deployment/addresses-" + network.name() + "-" + dtf.format(now) + ".json"
        writeFile(fileName, addresses)
        logger.lifecycle("contract addresses :: {}", fileName)
    }

    private void deployPrep() {
        try {
            TypeReference<Map<String, Object>> responseClass = new TypeReference<Map<String, Object>>() {}
            client._call(responseClass, DefaultScoreClient.ZERO_ADDRESS, "getPReps", Map.of("startRanking", 1, "endRanking", 100))
        } catch (JsonrpcClient.JsonrpcError ignored) {
            send(DefaultScoreClient.ZERO_ADDRESS, BigInteger.valueOf(2000) * ConfigureBalancedEnv.ICX, "registerPRep",
                    Map.of("name", "test",
                            "email", "kokoa@example.com",
                            "country", "USA",
                            "city", "New York",
                            "website", "https://icon.kokoa.com",
                            "details", "https://icon.kokoa.com/json/details.json",
                            "p2pEndpoint", "localhost:9082"))
        }
    }

    private void send(Address address, BigInteger value, String method, Map<String, Object> params) {
        client.send(wallet, address, value, method, params, DefaultICONClient.DEFAULT_RESULT_TIMEOUT)
    }


    private Address deploy(Score score) throws URISyntaxException {
        return _deploy(score, DefaultICONClient.ZERO_ADDRESS)
    }

    private Address _deploy(Score score, Address zeroAddress) {
        Map<String, Object> params = score.getParams()
        return client.deploy(wallet, zeroAddress, score.getPath(), params, score.getContentType())
    }


    private Address update(Score score) throws URISyntaxException {
        return _deploy(score, score.getAddress())
    }

    private List<Score> readSCOREs() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper()
        InputStream is = this.getClass()
                .getClassLoader()
                .getResourceAsStream(configFile.get())

        List<Score> list = objectMapper.readValue(is, new TypeReference<List<Score>>() {})

        list.sort((s1, s2) -> Float.compare(s1.getOrder(), s2.getOrder()))
        return list
    }


    private static void writeFile(String filePath, Map<String, Address> data) {
        Path outFile = Paths.get(filePath)
        try {
            ObjectMapper mapper = new ObjectMapper()
            String json = mapper.writeValueAsString(data)
            Files.write(outFile, json.getBytes())

        } catch (IOException e) {
            throw new RuntimeException(e.getMessage())
        }
    }
}
