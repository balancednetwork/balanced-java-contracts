package network.balanced.score.lib.structs;

import score.*;

public class RouteAction {

    public Integer action;
    public Address toAddress;

    public RouteAction(){};

    public RouteAction(Integer action, Address toAddress) {
        this.action = action;
        this.toAddress = toAddress;
    }


}
