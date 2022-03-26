package network.balanced.score.token;

import score.Address;
import score.Context;

public abstract class BaseScore {

	public abstract String getTag();
	public abstract Address getGovernance();
	public abstract Address getAdmin();

	public void onlyOwner() {
		Address sender = Context.getCaller();
		Address owner = Context.getOwner();
		if (sender == null || !sender.equals(owner)){
			Context.revert(getTag() + ": SenderNotScoreOwnerError:  (sender)"+ sender + " (owner)"+owner);
		}
	}

	public void onlyGovernance() {
		Address sender = Context.getCaller();
		Address governance = getGovernance();
		if (sender == null || !sender.equals(governance)) {
			Context.revert(
					getTag() + ": SenderNotGovernanceError: (sender)"+sender+" (governance)"+governance);
		}
	}

	public void onlyAdmin() {
		Address sender = Context.getCaller();
		Address admin = getAdmin();
		if (sender == null || !sender.equals(admin)) {
			Context.revert(getTag() + ": SenderNotAuthorized("+sender+")");
		}

	}

	
}
