package ai.ibriz.core.score.interfaces;

import score.Address;
import score.Context;

import java.util.Map;

public interface SystemInterface {
    Map<String, Object> getIISSInfo();

    Map<String, Object> getStake(Address address);

    Map<String, Object> getDelegation(Address address);

}
