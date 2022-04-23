package network.balanced.score.core.governance.interfaces;

import score.Address;

import foundation.icon.score.client.ScoreInterface;
import network.balanced.score.lib.structs.Disbursement;

@ScoreInterface
public interface Daofund extends Setter {
    boolean disburse(Address _recipient, Disbursement[] _amounts);
} 
