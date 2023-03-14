# Balanced OTC

Orders can only be created by the Daofund through token transfers.

Only supported payout token currently is bnUSD.

Balanced OTC trades tokens for bnUSD based on oracle pricing with a discount to allow the balanced dao to acquire different tokens cheap and efficiently.


## Creating a order
To create a order send bnUSD from daofund where the _data field is a the address of the wanted token.
This is done via a vote action on the format:
```
{
    "address": "<Daofund address>",
    "method": "disburse",
    "parameters": [
        {
            "type": "Address",
            "value": "<bnUSD address>"
        },
        {
            "type": "Address",
            "value": "<Balanced OTC address>"
        },
        {
            "type": "int",
            "value": "<Amount of bnUSD/OrderSize>"
        },
        {
            "type": "bytes",
            "value": "<Hex String of wanted token address>"
        }
    ]
}
```


## Setting and changing a discount
The discount is set per token and defaults to 1% if not set and is has a maximum of 7.5%.
Discounts can be set via a vote action on the format:
```
{
    "address": "<Balanced OTC address>",
    "method": "setDiscount",
    "parameters": [
        {
            "type": "Address",
            "value": "<bnUSD address>"
        },
        {
            "type": "int",
            "value": "<Discount in points>"
        }
    ]
}
```


## Canceling a Order
Canceling a order returns deposited bnUSD to the daofund.
A order can be canceled via a vote action on the format:
```
{
    "address": "<Balanced OTC address>",
    "method": "cancelOrder",
    "parameters": [
        {
            "type": "Address",
            "value": "<bnUSD address>"
        }
    ]
}
```


## Purchasing from a order
To purchase a order simply send tokens to the contract. Since there can currently one be one order per token.
If the payout bnUSD is equal to or less than the order size the order will be filled.

A purchase can be tested via:
```
@External(readonly = true)
BigInteger getExpectedBnUSDAmount(Address token, BigInteger amount);
```

Discounts can be read via:

```
@External(readonly = true)
BigInteger getDiscount(Address token);
```
Discount are denoted in POINTS, 100% = 10000


## Viewing orders

```
@External(readonly = true)
List<Map<String, Object>> getOrders();
```

Returns a List of orders represented as Maps.
A order map contains the keys:

token: The token wanted by the order/daofund.

orderSize: The amount of bnUSD available to purchase.

discount: The discount for this order.
