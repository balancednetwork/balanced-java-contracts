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
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

public class IRC721 {

  // ================================================
  // Consts
  // ================================================
  // Zero Address
  protected static final Address ZERO_ADDRESS = new Address(new byte[Address.LENGTH]);
  // Token name
  private final String name;
  // Token symbol
  private final String symbol;

  // ================================================
  // DB Variables
  // ================================================
  // Mapping from token ID to owner address
  private final DictDB<BigInteger, Address> _owners = Context.newDictDB("owners", Address.class);
  // Mapping owner address to token count
  private final DictDB<Address, BigInteger> _balances = Context.newDictDB("balances", BigInteger.class);
  // Mapping from token ID to approved address
  private final DictDB<BigInteger, Address> _tokenApprovals = Context.newDictDB("tokenApprovals", Address.class);
  // Mapping from owner to operator approvals
  private final BranchDB<Address, DictDB<Address, Boolean>> _operatorApprovals = Context.newBranchDB("operatorApprovals", Boolean.class);

  // ================================================
  // Event Logs
  // ================================================
  @EventLog
  protected void Transfer(Address from, Address to, BigInteger tokenId) {}

  @EventLog
  protected void Approval(Address ownerOf, Address to, BigInteger tokenId) {}

  @EventLog
  protected void ApprovalForAll(Address owner, Address operator, boolean approved) {}

  // ================================================
  // Methods
  // ================================================
  public IRC721 (String name, String symbol) {
    this.name = name;
    this.symbol = symbol;
  }

  @External(readonly = true)
  public BigInteger balanceOf(Address owner) {
    require(!owner.equals(ZERO_ADDRESS), "IRC721: balance query for the zero address");
    return _balances.getOrDefault(owner, ZERO);
  }

  @External(readonly = true)
  public Address ownerOf(BigInteger tokenId) {
    Address owner = _owners.getOrDefault(tokenId, ZERO_ADDRESS);
    require(!owner.equals(ZERO_ADDRESS), "IRC721: owner query for nonexistent token");
    return owner;
  }

  @External(readonly = true)
  public String name () {
    return this.name;
  }

  @External(readonly = true)
  public String symbol () {
    return this.symbol;
  }

  @External(readonly = true)
  public String tokenURI(BigInteger tokenId)  {
    require(_exists(tokenId), "IRC721Metadata: URI query for nonexistent token");

    String baseURI = _baseURI();
    return baseURI.length() > 0 ? baseURI + "|" + tokenId.toString() : "";
  }

  /**
   * @dev Base URI for computing {tokenURI}. If set, the resulting URI for each
   * token will be the concatenation of the `baseURI` and the `tokenId`. Empty
   * by default, can be overriden in child contracts.
   */
  protected String _baseURI() {
    return "";
  }

  @External
  public void approve(Address to, BigInteger tokenId) {
    Address owner = ownerOf(tokenId);
    require(to != owner, "IRC721: approval to current owner");
    final Address caller = Context.getCaller();

    require(caller.equals(owner) || isApprovedForAll(owner, caller),
        "IRC721: approve caller is not owner nor approved for all"
    );

    _approve(to, tokenId);
  }

  @External(readonly = true)
  public Address getApproved(BigInteger tokenId) {
    require(_exists(tokenId), "IRC721: approved query for nonexistent token");

    return _tokenApprovals.getOrDefault(tokenId, ZERO_ADDRESS);
  }

  @External
  public void setApprovalForAll(Address operator, boolean approved) {
    final Address caller = Context.getCaller();
    _setApprovalForAll(caller, operator, approved);
  }

  @External(readonly = true)
  public boolean isApprovedForAll(Address owner, Address operator) {
    return _operatorApprovals.at(owner).getOrDefault(operator, false);
  }
  
  @External
  public void transferFrom(
    Address from,
    Address to,
    BigInteger tokenId
  ) {
    final Address caller = Context.getCaller();
      require(_isApprovedOrOwner(caller, tokenId), "IRC721: transfer caller is not owner nor approved");
      _transfer(from, to, tokenId);
  }

  @External
  public void safeTransferFrom(
    Address from,
    Address to,
    BigInteger tokenId,
    @Optional byte[] _data
  ) {
    final Address caller = Context.getCaller();
    require(_isApprovedOrOwner(caller, tokenId), "IRC721: transfer caller is not owner nor approved");
    _safeTransfer(from, to, tokenId, _data);
  }
  
  /**
   * @dev Safely transfers `tokenId` token from `from` to `to`, checking first that contract recipients
   * are aware of the IRC721 protocol to prevent tokens from being forever locked.
   *
   * `_data` is additional data, it has no specified format and it is sent in call to `to`.
   *
   * This internal function is equivalent to {safeTransferFrom}, and can be used to e.g.
   * implement alternative mechanisms to perform token transfer, such as signature-based.
   *
   * Requirements:
   *
   * - `from` cannot be the zero address.
   * - `to` cannot be the zero address.
   * - `tokenId` token must exist and be owned by `from`.
   * - If `to` refers to a smart contract, it must implement {IIRC721Receiver-onIRC721Received}, which is called upon a safe transfer.
   *
   * Emits a {Transfer} event.
   */
  private void _safeTransfer(
      Address from,
      Address to,
      BigInteger tokenId,
      @Optional byte[] _data
  ) {
    _transfer(from, to, tokenId);
    require(_checkOnIRC721Received(from, to, tokenId, _data), "IRC721: transfer to non IRC721Receiver implementer");
  }
  
  /**
   * @dev Returns whether `tokenId` exists.
   *
   * Tokens can be managed by their owner or approved accounts via {approve} or {setApprovalForAll}.
   *
   * Tokens start existing when they are minted (`_mint`),
   * and stop existing when they are burned (`_burn`).
   */
  protected boolean _exists(BigInteger tokenId) {
    return _owners.get(tokenId) != null;
  }


  /**
   * @dev Returns whether `spender` is allowed to manage `tokenId`.
   *
   * Requirements:
   *
   * - `tokenId` must exist.
   */
  protected boolean _isApprovedOrOwner(Address spender, BigInteger tokenId) {
    require(_exists(tokenId), "IRC721: operator query for nonexistent token");
    Address owner = ownerOf(tokenId);
    return (spender.equals(owner) || getApproved(tokenId).equals(spender) || isApprovedForAll(owner, spender));
  }
  
  /**
   * @dev Safely mints `tokenId` and transfers it to `to`.
   *
   * Requirements:
   *
   * - `tokenId` must not exist.
   * - If `to` refers to a smart contract, it must implement {IIRC721Receiver-onIRC721Received}, which is called upon a safe transfer.
   *
   * Emits a {Transfer} event.
   */
  protected void _safeMint(
    Address to, 
    BigInteger tokenId,
    @Optional byte[] _data
  ) {
    _mint(to, tokenId);
    require(
        _checkOnIRC721Received(ZERO_ADDRESS, to, tokenId, _data),
        "IRC721: transfer to non IRC721Receiver implementer"
    );
  }
  
  /**
   * @dev Mints `tokenId` and transfers it to `to`.
   *
   * WARNING: Usage of this method is discouraged, use {_safeMint} whenever possible
   *
   * Requirements:
   *
   * - `tokenId` must not exist.
   * - `to` cannot be the zero address.
   *
   * Emits a {Transfer} event.
   */
  protected void _mint(Address to, BigInteger tokenId) {
    require(!to.equals(ZERO_ADDRESS), "IRC721: mint to the zero address");
    require(!_exists(tokenId), "IRC721: token already minted");

    _beforeTokenTransfer(ZERO_ADDRESS, to, tokenId);

    _balances.set(to, _balances.getOrDefault(to, ZERO).add(ONE));
    _owners.set(tokenId, to);

    this.Transfer(ZERO_ADDRESS, to, tokenId);
  }

  /**
   * @dev Destroys `tokenId`.
   * The approval is cleared when the token is burned.
   *
   * Requirements:
   *
   * - `tokenId` must exist.
   *
   * Emits a {Transfer} event.
   */
  protected void _burn (BigInteger tokenId) {
      Address owner = ownerOf(tokenId);

      _beforeTokenTransfer(owner, ZERO_ADDRESS, tokenId);

      // Clear approvals
      _approve(ZERO_ADDRESS, tokenId);

      _balances.set(owner, _balances.get(owner).subtract(ONE));
      _owners.set(tokenId, null);

      this.Transfer(owner, ZERO_ADDRESS, tokenId);
  }

  /**
   * @dev Transfers `tokenId` from `from` to `to`.
   *  As opposed to {transferFrom}, this imposes no restrictions on Context.getCaller().
   *
   * Requirements:
   *
   * - `to` cannot be the zero address.
   * - `tokenId` token must be owned by `from`.
   *
   * Emits a {Transfer} event.
   */
  protected void _transfer(
      Address from,
      Address to,
      BigInteger tokenId
  ) {
      require(ownerOf(tokenId).equals(from), "IRC721: transfer of token that is not own");
      require(!to.equals(ZERO_ADDRESS), "IRC721: transfer to the zero address");

      _beforeTokenTransfer(from, to, tokenId);

      // Clear approvals from the previous owner
      _approve(ZERO_ADDRESS, tokenId);

      _balances.set(from, _balances.get(from).subtract(ONE));
      _balances.set(to, _balances.getOrDefault(to, ZERO).add(ONE));
      _owners.set(tokenId, to);

      this.Transfer(from, to, tokenId);
  }
  
  /**
   * @dev Approve `to` to operate on `tokenId`
   *
   * Emits a {Approval} event.
   */
  protected void _approve(Address to, BigInteger tokenId) {
    _tokenApprovals.set(tokenId, to);
    this.Approval(ownerOf(tokenId), to, tokenId);
  }

  /**
   * @dev Approve `operator` to operate on all of `owner` tokens
   *
   * Emits a {ApprovalForAll} event.
   */
  protected void _setApprovalForAll(
      Address owner,
      Address operator,
      boolean approved
  ) {
      require(owner != operator, "IRC721: approve to caller");
      _operatorApprovals.at(owner).set(operator, approved);
      this.ApprovalForAll(owner, operator, approved);
  }
  
  /**
   * @dev Internal function to invoke {IIRC721Receiver-onIRC721Received} on a target address.
   * The call is not executed if the target address is not a contract.
   *
   * @param from address representing the previous owner of the given token ID
   * @param to target address that will receive the tokens
   * @param tokenId uint256 ID of the token to be transferred
   * @param _data bytes optional data to send along with the call
   * @return bool whether the call correctly returned the expected magic value
   */
  protected boolean _checkOnIRC721Received(
      Address from,
      Address to,
      BigInteger tokenId,
      byte[] _data
  ) {
    if (to.isContract()) {
      IIRC721Receiver.onIRC721Received(to, Context.getCaller(), from, tokenId, _data != null ? _data : "".getBytes());
    }

    return true;
  }
  
  /**
   * @dev Hook that is called before any token transfer. This includes minting
   * and burning.
   *
   * Calling conditions:
   *
   * - When `from` and `to` are both non-zero, ``from``'s `tokenId` will be
   * transferred to `to`.
   * - When `from` is zero, `tokenId` will be minted for `to`.
   * - When `to` is zero, ``from``'s `tokenId` will be burned.
   * - `from` and `to` are never both zero.
   *
   * To learn more about hooks, head to xref:ROOT:extending-contracts.adoc#using-hooks[Using Hooks].
   */
  protected void _beforeTokenTransfer(
      Address from,
      Address to,
      BigInteger tokenId
  ) {}
}
