package network.balanced.score.tokens.balancedtoken;

import score.Context;
import score.VarDB;

import static network.balanced.score.tokens.balancedtoken.Constants.*;

public class BalancedTokenVariables {
    static  final VarDB<String> currentVersion = Context.newVarDB(VERSION, String.class);

}
