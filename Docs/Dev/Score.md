## Naming
All Contracts should implement the Name method the name has to be added to score-lib/src/main/java/network/balanced/score/lib/utils/Names.java. Also new contracts should be added to the AddressManager. 

## Interfaces
All contracts has to implement a interface to allow for easy testing. The should also be decorated with both @ScoreClient and @ScoreInterface.

## Address Manager
The balanced address manager should be used to handle all addresses in the balanced ecosystem. 
To initialize the address manager the governance address has to set.
The contract also has to implement the AddressManager interface.

## Privileged access 
The Check class score-lib/src/main/java/network/balanced/score/lib/utils/Check.java has helper method to restrict access to external methods. Use these methods instead of creating new ones. onlyGovernance is preferred to onlyOwner.