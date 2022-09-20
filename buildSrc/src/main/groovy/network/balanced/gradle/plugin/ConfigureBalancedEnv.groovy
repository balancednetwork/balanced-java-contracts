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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import foundation.icon.icx.Wallet
import foundation.icon.jsonrpc.Address
import foundation.icon.score.client.DefaultScoreClient
import network.balanced.gradle.plugin.utils.Action
import network.balanced.gradle.plugin.utils.Network
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class ConfigureBalancedEnv extends DefaultTask {
    static final BigInteger ICX = BigInteger.TEN.pow(18)

    private Properties _properties = new Properties()

    private Map<String, Object> properties = new HashMap<>()

    private Property<String> keystore
    private Property<String> password
    private Property<String> actionsFilePath
    private Property<String> addressesFilePath
    private final Property<String> propertiesFile

    private DefaultICONClient client
    private Wallet wallet
    private Network network

    private Map<String, Address> addresses

    static String getTaskName() {
        return "executeBalancedActions"
    }

    ConfigureBalancedEnv() {
        super()
        ObjectFactory objectFactory = getProject().getObjects()
        this.keystore = objectFactory.property(String.class)
        this.password = objectFactory.property(String.class)
        this.actionsFilePath = objectFactory.property(String.class)
        this.addressesFilePath = objectFactory.property(String.class)
        this.propertiesFile = objectFactory.property(String.class)
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
    Property<String> getActionsFile() {
        return actionsFilePath
    }

    void setKeystore(String keystore) {
        this.keystore.set(keystore)
    }

    void setPassword(String password) {
        this.password.set(password)
    }

    void setActionsFile(String actionFile) {
        this.actionsFilePath.set(actionFile)
    }

    void setContractAddressFile(String contractAddress) {
        this.addressesFilePath.set(contractAddress)
    }

    void setPropertiesFile(String configFile) {
        this.propertiesFile.set(configFile)
    }


    @TaskAction
    void configure() throws Exception {

        loadAddresses()

        List<Action> actions = loadActions()

        init()
        client = new DefaultICONClient(this.network)

        this.wallet = DefaultScoreClient.wallet(this.keystore.get(), this.password.get())


        logger.lifecycle('executing contract configurations...')

        for (Action action : actions) {
            logger.lifecycle("executing action $action.contract :: $action.method")

            JsonNode node = action.getArgs()
            Map<String, Object> params = new HashMap<>()
            buildParams(node, params)
            action.setParams(params)

            execute(action)
        }
    }

    private void buildParams(JsonNode node, Map<String, Object> params) {
        Iterator<Map.Entry<String, JsonNode>> iter = node.fields()
        while (iter.hasNext()) {
            Map.Entry<String, JsonNode> entry = iter.next()
            String key = entry.getKey()
            switch (key) {
                case "__address_args__":
                    JsonNode addressNodes = entry.getValue()
                    Iterator<Map.Entry<String, JsonNode>> addressIter = addressNodes.fields()
                    while (addressIter.hasNext()) {
                        Map.Entry<String, JsonNode> nodeEntry = addressIter.next()
                        params.put(nodeEntry.getKey(), getAddress(nodeEntry.getValue().textValue()))
                    }
                    break
                case "__property_args__":
                    JsonNode propertyNode = entry.getValue()
                    Iterator<Map.Entry<String, JsonNode>> propertyIter = propertyNode.fields()
                    while (propertyIter.hasNext()) {
                        Map.Entry<String, JsonNode> nodeEntry = propertyIter.next()
                        params.put(nodeEntry.getKey(), getMapProperty(nodeEntry.getValue().textValue()))
                    }
                    break
                case "__args__":
                    JsonNode argsNode = entry.getValue()
                    Map<String, Object> _params = new HashMap<>()
                    buildParams(argsNode, _params)
                    break
                default:
                    params.put(entry.getKey(), parse(entry.getValue()))
            }

        }
    }


    private Object parse(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iter = jsonNode.fields()
            Map<String, Object> params = new HashMap<>()
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next()
                JsonNode value = entry.getValue()
                if (value.isValueNode()) {
                    params.put(entry.getKey(), value.textValue())
                } else {
                    buildParams(jsonNode, params)
                }
            }
            return params
        } else if (jsonNode.isArray()) {
            Iterator<JsonNode> node = jsonNode.elements()
            List<Object> list = new ArrayList<>()
            while (node.hasNext()) {
                list.add(parse(node.next()))
            }
            return list
        }
        return jsonNode.textValue()
    }

    private void execute(Action action) {
        BigInteger value = action.value == null ? BigInteger.ZERO : action.value
        client.send(wallet, getAddress(action.contract), value, action.method, action.params, DefaultScoreClient.DEFAULT_RESULT_TIMEOUT)
    }

    private Address getAddress(String key) {
        return this.addresses.get(key)
    }

    private Object getMapProperty(String key) {
        return this.properties.get(key)
    }


    private List<Action> loadActions() throws IOException {
        logger.lifecycle('loading actions...')
        InputStream is = this.getClass()
                .getClassLoader()
                .getResourceAsStream(this.actionsFilePath.get())
        if (is == null) {
            throw new RuntimeException(this.actionsFilePath.get() + " file not found")
        }

        ObjectMapper objectMapper = new ObjectMapper()

        List<Action> list = objectMapper.readValue(is, new TypeReference<List<Action>>() {
        })

        list.sort((s1, s2) -> Float.compare(s1.getOrder(), s2.getOrder()))
        return list
    }

    private void loadAddresses() {
        logger.lifecycle('loading addresses...')
        InputStream is = this.getClass()
                .getClassLoader()
                .getResourceAsStream(this.addressesFilePath.get())
        if (is == null) {
            throw new RuntimeException(this.addressesFilePath.get() + " file not found")
        }
        ObjectMapper objectMapper = new ObjectMapper()

        this.addresses = objectMapper.readValue(is, new TypeReference<HashMap<String, Address>>() {
        })

    }


    private void init() {
        logger.lifecycle('loading properties...')
        InputStream is = this.getClass()
                .getClassLoader()
                .getResourceAsStream(this.propertiesFile.get())
        if (is == null) {
            throw new RuntimeException(this.propertiesFile.get() + " file not found")
        }
        this._properties.load(is)
        this.network = Network.getNetwork(_properties.getProperty("NETWORK"))

        String address = this._properties.getProperty("ORACLE_ADDRESS")
        Address ORACLE_ADDRESS = new Address(address)
        properties.put("ORACLE_ADDRESS", ORACLE_ADDRESS)

        // delegate methods
        List<String> prepsAddress = _properties.getProperty("PREP_LIST").split("--;--").toList()
        List<String> vote = _properties.getProperty("VOTES_IN_PER").split("--;--").toList()
        assert (prepsAddress.size() == vote.size())
        List<Map<String, Object>> delegationParam = new ArrayList<Map>()
        for (int i = 0; i < vote.size(); i++) {
            Map<String, Object> hashMap = new HashMap<>()
            address = new Address(prepsAddress[i])
            hashMap["_address"] = new Address(address)
            hashMap["_votes_in_per"] = vote[i].toBigInteger() * BigInteger.TEN.pow(18)
            delegationParam.add(hashMap)
        }

        properties.put("DELEGATE_PARAM", delegationParam)
        boolean status_param = this._properties.getProperty("STATUS")
        properties.put("STATUS_PARAM", status_param)

        Address[] acceptedTokens = new Address[3];
        acceptedTokens[0] = addresses.get("sicx")
        acceptedTokens[1] = addresses.get("bnUSD")
        acceptedTokens[2] = addresses.get("baln")
        properties.put("ACCEPTED_TOKENS", acceptedTokens)

        properties.put("FEE_INTERVAL_IN_BLOCKS", this._properties.getProperty("FEE_INTERVAL_IN_BLOCKS"))
    }
}