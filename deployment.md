## Deployment Steps
The gradle.properties file consists of keystore and password. The keystoreName can either be an absolute path or relative to root of the project directory. Keystore should be kept in `.keystores` directory and accessed from that directory. 
```properties
keystoreName=.keystores/keystore.json
keystorePass=keystore_password
```

The configuration for the deployement is present in `build.gradle`.
```gradle
deployBalancedContracts  {
    envs {
        local {
            env = "local"
        }
        berlin {
            env = "berlin"
            configFile = "contracts-sample.json"
            keystore = rootProject.findProperty('keystoreName') ?: ''
            password = rootProject.findProperty('keystorePass') ?: ''
        }
        sejong {
            env = "sejong"
            configFile = "contracts-sample.json"
            keystore = rootProject.findProperty('keystoreName') ?: ''
            password = rootProject.findProperty('keystorePass') ?: ''
        }
    }
}
```

Every network configuration contains `env`, `configFile`, `keystore`, `password`. 
* `configFile` is the name of the file for the deployment configuration. The file should be present in ``buildSrc/src/main/resources/`. The contracts specified in `configFile` are deployed. The JSON file contains array of objects and each object deploys a contract. 
Here, `name` parameter specifies the name of the contract, `path` specifies what to be deployed. `path` can be linked to any zip file present in any network. `params` is an object that contains the parameter required for the deployment of the contract. `order` specifies the order at which the contract is to be deployed. If two contracts have the same order then they can be deployed in any sequence and if the order is not specified the value of the order is assumed to be `0`.
```json
{
    "name": "governance",
    "path": "https://main.tracker.solidwallet.io/score/cx44250a12074799e26fdeee75648ae47e2cc84219_23.zip",
    "params": {
    },
    "order": 1.0
}
```

Once we are done with the process, we can simply deploy the contract to the network we wish for through the gradle menu. 
![image](https://user-images.githubusercontent.com/98825512/171312243-d43abffa-216f-4737-9a43-0dc2ef319599.png)



## Configuration Steps
After the contract is deployed, a json file such as `addresses-BERLIN-2022-05-31-21-22.json` is created in the `.deployment` folder.  The received filename should be added to `contractAddressFile` parameter of `executeBalancedActions` in `build.gradle`. The resulting action then should be as follows:

```gradle
executeBalancedActions {
    contractAddressFile = "addresses-BERLIN-2022-05-31-20-45.json"
    propertiesFile = "sample.properties"
    actionsFile = "actions-sample.json"
    keystore = rootProject.findProperty('keystoreName') ?: ''
    password = rootProject.findProperty('keystorePass') ?: ''
}
```
If `keystoreName` and `keystorePass` is not present in `gradle.properties`, we should put `keystoreName` and `keystorePass` here in **executeBalancedAction** as well. 

Now, before running the `executeBalancedActions` task we need to set the network in `buildSrc/src/main/resources/sample.properties`. In `sample.properties` file, we need to set the `NETWORK` to either `sejong` or `berlin` based on which network the contracts are deployed. Similarly, other parameters of the `sample.properties` should be changed based on the network. We can get the address of the contracts from [here](https://github.com/balancednetwork/balanced-java-contracts/wiki/Contract-Addresses). The addresses and votes percentage of the preps should be separated by delimiter `--;--`. You need to give one of the  prep addresses of that network,  you can simply give the first address from the governance prep list. For the berlin network, you can find it [here](http://berlin.tracker.solidwallet.io/governance). The sum of every number in `VOTES_IN_PER` needs to be `100`.

```properties
NETWORK=berlin
ORACLE_ADDRESS=cx900e2d17c38903a340a0181523fa2f720af9a798
STAKEDLP_ADDRESS=cx0c3848f0fbb714fcb104e909e3fc1250b8cf9e7f
PREP_LIST=hx38f35eff5e5516b48a713fe3c8031c94124191f0--;--hx38f35eff5e5516b48a713fe3c8031c94124191f0
VOTES_IN_PER=50--;--50
```

Finally, we can run `executeBalancedActions` task from `gradle` menu in IntelliJ IDEA IDE.
