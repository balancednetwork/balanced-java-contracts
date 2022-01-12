package network.balanced.score.tokens.utils;

public enum ErrorCodes {
    InvalidOperation(81);

    private final int code;


    ErrorCodes(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
