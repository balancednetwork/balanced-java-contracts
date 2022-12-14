## Guidelines
* Only test and deploy one contract in unit tests, mock the rest.
* Use MockBalanced class to mock all balanced specific contracts.
* Test should follow Arrange-Act-Assert pattern as much as possible.

## MockBalanced
This class creates mocks of all the contracts in balanced. If the contract uses the address manager everything will be handled automatically if the MockBalanced governance address is used in the contract setup.
This means the contract being tested will automatically call the mocked contract in MockBalanced.

## MockContract
This class is the underlying class that to use when mocking a contract dependency. To mock a interface you create a MockContract of the contract interface.
From this class you can get the address, account and the mock. The account can be used to make contract calls from the "contract".

## Jacoco 
Jacoco is used to generate test coverage for out unit test. The result are generated in the build folder. 
