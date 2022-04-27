/*
 * Copyright 2021 ICONLOOP Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package score;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

public class ObjectSerializerTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();

    public static class Point {
        public BigInteger x;
        public BigInteger y;

        Point(BigInteger x, BigInteger y) {
            this.x = x;
            this.y = y;
        }

        public static void writeObject(ObjectWriter w, Point obj) {
            w.write(obj.x);
            w.write(obj.y);
        }

        public static Point readObject(ObjectReader r) {
            return new Point(
                    r.readBigInteger(),
                    r.readBigInteger()
            );
        }
    }

    public static class PointsScore {
        public PointsScore() {
            final DictDB<String, Point> points = Context.newDictDB("points", Point.class);
            Point p1 = new Point(BigInteger.valueOf(100), BigInteger.valueOf(200));
            points.set("p1", p1);
            // Modify p1 locally
            p1.x = BigInteger.ONE;

            // Now oldP1 and p1 should be different.
            Point oldP1 = points.get("p1");
            Context.require(!oldP1.x.equals(p1.x));
            Context.require(BigInteger.ONE.equals(p1.x));
            Context.require(BigInteger.valueOf(100).equals(oldP1.x));
        }
    }

    @Test
    void testSetAndGet() throws Exception {
        // Must not throw AssertionError
        sm.deploy(owner, PointsScore.class);
    }
}
