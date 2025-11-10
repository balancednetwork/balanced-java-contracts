/*
 * Copyright (c) 2024 Balanced.network.
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

package team.iconation.standards.token.irc721;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static score.Context.require;

import java.math.BigInteger;

import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.External;

public class IRC721Enumerable extends IRC721 {
  // ================================================
  // DB Variables
  // ================================================
  // Mapping from owner to list of owned token IDs
  final BranchDB<Address, DictDB<BigInteger, BigInteger>> _ownedTokens;
  
  // Mapping from token ID to index of the owner tokens list
  final DictDB<BigInteger, BigInteger> _ownedTokensIndex;

  // Array with all token ids, used for enumeration
  final VarDB<BigInteger> _allTokensLength;
  final DictDB<BigInteger, BigInteger> _allTokens;

  // Mapping from token id to position in the allTokens array
  final DictDB<BigInteger, BigInteger> _allTokensIndex;

  // ================================================
  // Methods
  // ================================================
  public IRC721Enumerable(String name, String symbol) {
    super(name, symbol);

    _ownedTokens = Context.newBranchDB(symbol + "_ownedTokens", BigInteger.class);
    _ownedTokensIndex = Context.newDictDB(symbol + "_ownedTokensIndex", BigInteger.class);
    _allTokensLength = Context.newVarDB(symbol + "_allTokensLength", BigInteger.class);
    _allTokens = Context.newDictDB(symbol + "_allTokens", BigInteger.class);
    _allTokensIndex = Context.newDictDB(symbol + "_allTokensIndex", BigInteger.class);

    if (_allTokensLength.get() == null) {
      _allTokensLength.set(ZERO);
    }
  }
  
  /**
   * @dev Returns a token ID owned by `owner` at a given `index` of its token list.
   * Use along with {balanceOf} to enumerate all of ``owner``'s tokens.
   */
  @External(readonly = true)
  public BigInteger tokenOfOwnerByIndex (Address owner, BigInteger index) {
    require(index.compareTo(balanceOf(owner)) < 0, 
      "tokenOfOwnerByIndex: owner index out of bounds");
    return this._ownedTokens.at(owner).get(index);
  }
  
  /**
   * @dev Returns the total amount of tokens stored by the contract.
   */
  @External(readonly = true)
  public BigInteger totalSupply() {
    return _allTokensLength.get();
  }

  /**
   * @dev Returns a token ID at a given `index` of all the tokens stored by the contract.
   * Use along with {totalSupply} to enumerate all tokens.
   */
  @External(readonly = true)
  public BigInteger tokenByIndex(BigInteger index) {
    require(index.compareTo(totalSupply()) < 0, 
      "tokenByIndex: global index out of bounds");
    return _allTokens.get(index);
  }
  
  /**
   * @dev Hook that is called before any token transfer. 
   * This includes minting and burning.
   *
   * Calling conditions:
   *
   * - When `from` and `to` are both non-zero, ``from``'s `tokenId` will be
   * transferred to `to`.
   * - When `from` is zero, `tokenId` will be minted for `to`.
   * - When `to` is zero, ``from``'s `tokenId` will be burned.
   * - `from` cannot be the zero address.
   * - `to` cannot be the zero address.
   */
  @Override
  protected void _beforeTokenTransfer(
      Address from,
      Address to,
      BigInteger tokenId
  ) {
    super._beforeTokenTransfer(from, to, tokenId);

    if (from.equals(ZERO_ADDRESS)) {
        _addTokenToAllTokensEnumeration(tokenId);
    } else if (from != to) {
        _removeTokenFromOwnerEnumeration(from, tokenId);
    }
    if (to.equals(ZERO_ADDRESS)) {
        _removeTokenFromAllTokensEnumeration(tokenId);
    } else if (to != from) {
        _addTokenToOwnerEnumeration(to, tokenId);
    }
  }
  
  /**
   * @dev Private function to add a token to this extension's ownership-tracking data structures.
   * @param to address representing the new owner of the given token ID
   * @param tokenId ID of the token to be added to the tokens list of the given address
   */
  private void _addTokenToOwnerEnumeration(Address to, BigInteger tokenId) {
    BigInteger length = balanceOf(to);
    _ownedTokens.at(to).set(length, tokenId);
    _ownedTokensIndex.set(tokenId, length);
  }

  /**
   * @dev Private function to add a token to this extension's token tracking data structures.
   * @param tokenId ID of the token to be added to the tokens list
   */
  private void _addTokenToAllTokensEnumeration(BigInteger tokenId) {
    BigInteger oldLength = _allTokensLength.get();
    _allTokensIndex.set(tokenId, oldLength);
    _allTokens.set(oldLength, tokenId);
    _allTokensLength.set(oldLength.add(ONE));
  }

  /**
   * @dev Private function to remove a token from this extension's ownership-tracking data structures. Note that
   * while the token is not assigned a new owner, the `_ownedTokensIndex` mapping is _not_ updated: this allows for
   * gas optimizations e.g. when performing a transfer operation (avoiding double writes).
   * This has O(1) time complexity, but alters the order of the _ownedTokens array.
   * @param from address representing the previous owner of the given token ID
   * @param tokenId ID of the token to be removed from the tokens list of the given address
   */
  private void _removeTokenFromOwnerEnumeration(Address from, BigInteger tokenId) {
    // To prevent a gap in from's tokens array, we store the last token in the index of the token to delete, and
    // then delete the last slot (swap and pop).

    BigInteger lastTokenIndex = balanceOf(from).subtract(ONE);
    BigInteger tokenIndex = _ownedTokensIndex.get(tokenId);

    // When the token to delete is the last token, the swap operation is unnecessary
    if (tokenIndex != lastTokenIndex) {
        BigInteger lastTokenId = _ownedTokens.at(from).get(lastTokenIndex);
        _ownedTokens.at(from).set(tokenIndex, lastTokenId); // Move the last token to the slot of the to-delete token
        _ownedTokensIndex.set(lastTokenId, tokenIndex); // Update the moved token's index
    }

    // This also deletes the contents at the last position of the array
    _ownedTokensIndex.set(tokenId, null);
    _ownedTokens.at(from).set(lastTokenIndex, null);
  }

  /**
   * @dev Private function to remove a token from this extension's token tracking data structures.
   * This has O(1) time complexity, but alters the order of the _allTokens array.
   * @param tokenId ID of the token to be removed from the tokens list
   */
  private void _removeTokenFromAllTokensEnumeration(BigInteger tokenId) {
    // To prevent a gap in the tokens array, we store the last token in the index of the token to delete, and
    // then delete the last slot (swap and pop).

    BigInteger lastTokenIndex = _allTokensLength.get().subtract(ONE);
    BigInteger tokenIndex = _allTokensIndex.get(tokenId);

    // When the token to delete is the last token, the swap operation is unnecessary. However, since this occurs so
    // rarely (when the last minted token is burnt) that we still do the swap here to avoid the gas cost of adding
    // an 'if' statement (like in _removeTokenFromOwnerEnumeration)
    BigInteger lastTokenId = _allTokens.get(lastTokenIndex);

    _allTokens.set(tokenIndex, lastTokenId); // Move the last token to the slot of the to-delete token
    _allTokensIndex.set(lastTokenId, tokenIndex); // Update the moved token's index

    // This also deletes the contents at the last position of the array
    _allTokensIndex.set(tokenId, null);
    // pop
    _allTokens.set(lastTokenIndex, null);
    _allTokensLength.set(lastTokenIndex);
  }
}
