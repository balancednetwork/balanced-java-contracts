package network.balanced.score.tokens.utils;

import score.Context;

public class Utils {

    public static void require(boolean condition, String message) {
        if (!condition)
            Context.revert(ErrorCodes.RequirementViolationException.getCode(), message);
    }
}
