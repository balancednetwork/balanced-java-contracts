package network.balanced.score.core.governance.interfaces;
import foundation.icon.score.client.ScoreInterface;
import score.Address;

@ScoreInterface
public interface Admin {
    void setAdmin(Address _admin);
}
