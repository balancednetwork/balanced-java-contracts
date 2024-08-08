package team.iconation.standards.token.irc721;

import java.math.BigInteger;
import score.Address;
import score.Context;

public class IIRC721Receiver {
  public static void onIRC721Received (
    Address to, 
    Address operator, 
    Address from, 
    BigInteger tokenId,
    Object data
  ) {
    Context.call(to, "onIRC721Received", operator, from, tokenId, data);
  }
}
