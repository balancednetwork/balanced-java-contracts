package network.balanced.score.core.governance;

public class ProposalStatus {
    public static final int PENDING = 0;
    public static final int ACTIVE = 1;
    public static final int CANCELLED = 2;
    public static final int DEFEATED = 3;
    public static final int SUCCEEDED = 4;
    public static final int NO_QUORUM = 5;
    public static final int EXECUTED = 6;
    public static final int FAILED_EXECUTION = 7;
    public static final String[] STATUS = new String[] {"Pending", "Active", "Cancelled", "Defeated", "Succeeded", "No Quorum", "Executed", "Failed Execution"};
}