package network.balanced.score.lib.structs;

import score.*;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

public class RouteData {


    public String method;
    public String receiver;
    public BigInteger minimumReceive;
    public List<RouteAction> actions;
    public RouteData(){}

    public RouteData(String method, String receiver, BigInteger minimumReceive, List<RouteAction> actions) {
        this.method = method;
        this.receiver = receiver;
        this.minimumReceive = minimumReceive;
        this.actions = actions;
    }

    public RouteData(String method,  List<RouteAction> actions) {
        this.method = method;
        this.actions = actions;
    }

    public static RouteData readObject(ObjectReader reader) {
        RouteData obj = new RouteData();
        reader.beginList();
        List<RouteAction> actions = new ArrayList<>();
        obj.method = reader.readString();
        obj.receiver = reader.readNullable(String.class);
        obj.minimumReceive = reader.readNullable((BigInteger.class));
        while (reader.hasNext()) {
            RouteAction data = reader.read(RouteAction.class);
            actions.add(data);
        }
        obj.actions = actions;
        reader.end();
        return obj;
    }

    public static void writeObject(ObjectWriter w, RouteData obj) {
        w.beginList(obj.actions.size()+3);
        w.write(obj.method);
        w.writeNullable(obj.receiver);
        w.writeNullable(obj.minimumReceive);
        for (RouteAction action : obj.actions) {
            w.write(action);
        }
        w.end();
    }

    public static RouteData fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return RouteData.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        RouteData.writeObject(writer, this);
        return writer.toByteArray();
    }

}
