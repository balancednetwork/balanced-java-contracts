## Deploment Steps
You need to pull add-deployment-plugin branch and then open the folder in IntelliJ IDEA IDE. 
Once it is opened , you need to create a file named gradle.properties .
The gradle.properties file consists of keystore and password.

```properties
keystoreName = keystore_file_location.json
keystorePass = keystore_password
```
Once we are done with the process, we can simply deploy the contract to the network we wish for through the gradle menu.


## Configuration Steps
After the contract is deployed, a json file such as `addresses-BERLIN-2022-05-31-21-22.json` is created in the `.deployment` folder.  The recieved filename should be added to `contractAddressFile` parameter of `executeBalancedActions` in `build.gradle`. The resulting action then should be like as follows:

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

Now, before running the `executeBalancedActions` task we need to set the network in `buildSrc/src/main/resources/sample.properties`. In `sample.properties` file we need to set the `NETWORK` to either `sejong` or `berlin` based on which network the contracts are deployed. Similarly, other parameters of the `sample.properties` should be changed based on the network. We can get the address of the contract from [here](https://github.com/balancednetwork/balanced-java-contracts/wiki/Contract-Addresses). The addresses and votes percentage of the preps should be seperated by delimiter `--;--`. You need to give one of the  prep address of that network,  you can simply give the first address from the governance prep list. For berlin network, you can find it [here](http://berlin.tracker.solidwallet.io/governance). The sum of every number in `VOTES_IN_PER` needs to be `100`.

```properties
NETWORK=berlin
ORACLE_ADDRESS=cx900e2d17c38903a340a0181523fa2f720af9a798
STAKEDLP_ADDRESS=cx0c3848f0fbb714fcb104e909e3fc1250b8cf9e7f
PREP_LIST=hx38f35eff5e5516b48a713fe3c8031c94124191f0--;--hx38f35eff5e5516b48a713fe3c8031c94124191f0
VOTES_IN_PER=50--;--50
```

Finally, we can run `executeBalancedActions` task from `gradle` menu in IntelliJ IDEA IDE.
