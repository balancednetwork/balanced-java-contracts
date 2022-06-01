## Deploment Steps

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
If `keystoreName` and `keystorePass` is not present in `gradle.properties`, we should put *keystoreName* and *keystorePass* here in **executeBalancedAction** as well. 

Now, before running the `executeBalancedActions` task we need to set the network in `buildSrc/src/main/resources/sample.properties`. In `sample.properties` file we need to set the `NETWORK` to either `sejong` or `berlin` based on which network the contracts are deployed. Similarly, other parameters of the `sample.properties` should be changed based on the network. We can get the address of the contract from [here](https://github.com/balancednetwork/balanced-java-contracts/wiki/Contract-Addresses). The addresses and votes percentage of the preps should be seperated by delimiter `--;--`.

Finally, we can run `executeBalancedActions` task from `gradle` menu in IntelliJ IDEA IDE.