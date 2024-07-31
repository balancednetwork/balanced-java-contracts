package network.balanced.score.lib.structs;

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

public class RouteAction {

    public Integer action;
    public Address toAddress;

    public RouteAction() {
    }

    public RouteAction(Integer action, Address toAddress) {
        this.action = action;
        this.toAddress = toAddress;
    }

    public static void writeObject(ObjectWriter writer, RouteAction obj) {
        obj.writeObject(writer);
    }

    public static RouteAction readObject(ObjectReader reader) {
        RouteAction obj = new RouteAction();
        reader.beginList();
        obj.action = reader.readInt();
        obj.toAddress = reader.readNullable(Address.class);
        reader.end();
        return obj;
    }

    public void writeObject(ObjectWriter writer) {
        writer.beginList(2);
        writer.write(this.action);
        writer.writeNullable(this.toAddress);
        writer.end();
    }

}
