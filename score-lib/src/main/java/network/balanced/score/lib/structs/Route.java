package network.balanced.score.lib.structs;

import score.ByteArrayObjectWriter;
import score.Context;
import score.ObjectReader;
import score.ObjectWriter;
import scorex.util.ArrayList;

import java.util.List;

public class Route {

    public List<RouteAction> actions;

    public Route() {
        this.actions = new ArrayList<>();
    }

    public Route(List<RouteAction> actions) {
        this.actions = actions;
    }

    public static Route readObject(ObjectReader reader) {
        Route obj = new Route();
        reader.beginList();
        List<RouteAction> actions = new ArrayList<>();
        while (reader.hasNext()) {
            RouteAction data = reader.read(RouteAction.class);
            actions.add(data);
        }
        obj.actions = actions;
        reader.end();
        return obj;
    }

    public static void writeObject(ObjectWriter w, Route obj) {
        w.beginList(obj.actions.size());
        for (RouteAction action : obj.actions) {
            w.write(action);
        }
        w.end();
    }

    public static Route fromBytes(byte[] bytes) {
        ObjectReader reader = Context.newByteArrayObjectReader("RLPn", bytes);
        return Route.readObject(reader);
    }

    public byte[] toBytes() {
        ByteArrayObjectWriter writer = Context.newByteArrayObjectWriter("RLPn");
        Route.writeObject(writer, this);
        return writer.toByteArray();
    }

}
