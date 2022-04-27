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

package score.impl;

import score.Address;
import score.ByteArrayObjectWriter;
import score.ObjectWriter;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class RLPObjectWriter implements ByteArrayObjectWriter {
    private static final int SHORT_BASE = 0x80;
    private static final int SHORT_LEN_LIMIT = 55;
    private static final int LONG_BASE = 0xb7;

    private final ArrayList<ByteArrayBuilder> frames = new ArrayList<>();
    private ByteArrayBuilder os;
    //
    private int level = 0;

    public RLPObjectWriter() {
        os = new ByteArrayBuilder();
        frames.add(os);
    }

    private void writeRLPString(byte[] bs) {
        int l = bs.length;
        if (l == 1 && (bs[0] & 0Xff) < SHORT_BASE) {
            os.write(bs[0]);
        } else if (l <= SHORT_LEN_LIMIT) {
            os.write(SHORT_BASE + l);
            os.write(bs, 0, l);
        } else if (l <= 0Xff) {
            os.write(LONG_BASE + 1);
            os.write(l);
            os.write(bs, 0, l);
        } else if (l <= 0Xffff) {
            os.write(LONG_BASE + 2);
            os.write(l >> 8);
            os.write(l);
            os.write(bs, 0, l);
        } else if (l <= 0Xffffff) {
            os.write(LONG_BASE + 3);
            os.write(l >> 16);
            os.write(l >> 8);
            os.write(l);
            os.write(bs, 0, l);
        } else {
            os.write(LONG_BASE + 4);
            os.write(l >> 24);
            os.write(l >> 16);
            os.write(l >> 8);
            os.write(l);
            os.write(bs, 0, l);
        }
    }

    @Override
    public void write(boolean v) {
        writeRLPString(BigInteger.valueOf(v ? 1 : 0).toByteArray());
    }

    @Override
    public void write(byte v) {
        writeRLPString(BigInteger.valueOf(v).toByteArray());
    }

    @Override
    public void write(short v) {
        writeRLPString(BigInteger.valueOf(v).toByteArray());
    }

    @Override
    public void write(char v) {
        writeRLPString(BigInteger.valueOf((int) v).toByteArray());
    }

    @Override
    public void write(int v) {
        writeRLPString(BigInteger.valueOf(v).toByteArray());
    }

    @Override
    public void write(float v) {
        int i = Float.floatToRawIntBits(v);
        os.write(SHORT_BASE + 4);
        os.write((i >> 24) & 0xff);
        os.write((i >> 16) & 0xff);
        os.write((i >> 8) & 0xff);
        os.write(i & 0xff);
    }

    @Override
    public void write(long v) {
        writeRLPString(BigInteger.valueOf(v).toByteArray());
    }

    @Override
    public void write(double v) {
        long i = Double.doubleToRawLongBits(v);
        os.write(SHORT_BASE + 8);
        os.write(((int) (i >> 54)) & 0xff);
        os.write(((int) (i >> 48)) & 0xff);
        os.write(((int) (i >> 40)) & 0xff);
        os.write(((int) (i >> 32)) & 0xff);
        os.write(((int) (i >> 24)) & 0xff);
        os.write(((int) (i >> 16)) & 0xff);
        os.write(((int) (i >> 8)) & 0xff);
        os.write(((int) i) & 0xff);
    }

    @Override
    public void write(BigInteger v) {
        writeRLPString(v.toByteArray());
    }

    @Override
    public void write(String v) {
        writeRLPString(v.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void write(byte[] v) {
        writeRLPString(v);
    }

    @Override
    public void write(Address v) {
        writeRLPString(v.toByteArray());
    }

    @Override
    public void write(Object v) {
        Objects.requireNonNull(v);

        var c = v.getClass();
        if (c == java.lang.Boolean.class) {
            write((boolean)v);
        } else if (c == java.lang.Byte.class) {
            write((byte)v);
        } else if (c == java.lang.Short.class) {
            write((short)v);
        } else if (c == java.lang.Character.class) {
            write((char)v);
        } else if (c == java.lang.Integer.class) {
            write((int)v);
        } else if (c == java.lang.Float.class) {
            write((float)v);
        } else if (c == java.lang.Long.class) {
            write((long)v);
        } else if (c == java.lang.Double.class) {
            write((double) v);
        } else if (c == java.math.BigInteger.class) {
            write((BigInteger) v);
        } else if (c == java.lang.String.class) {
            write((String) v);
        } else if (c == byte[].class) {
            write((byte[]) v);
        } else if (c == Address.class) {
            write((Address) v);
        } else {
            try {
                var m = c.getDeclaredMethod("writeObject", ObjectWriter.class, c);
                if ((m.getModifiers()& Modifier.STATIC) == 0
                        || (m.getModifiers()&Modifier.PUBLIC) == 0) {
                    throw new IllegalArgumentException();
                }
                m.invoke(null, this, v);
            } catch (NoSuchMethodException
                    | IllegalAccessException
                    | InvocationTargetException e) {
                e.printStackTrace();
                throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public void writeNullable(Object v) {
        writeNullity(v == null);
        if (v != null) {
            write(v);
        }
    }

    @Override
    public void write(Object... v) {
        for (Object e : v) {
            write(e);
        }
    }

    @Override
    public void writeNullable(Object... v) {
        for (Object e : v) {
            writeNullable(e);
        }
    }

    @Override
    public void writeNull() {
        os.write(0xf8);
        os.write(0x00);
    }

    private void writeNullity(boolean nullity) {
        if (nullity) {
            writeNull();
        }
    }

    @Override
    public void beginList(int l) {
        ++level;
        writeListHeader(l);
    }

    @Override
    public void writeListOf(Object... v) {
        writeListHeader(v.length);
        write(v);
        writeFooter();
    }

    @Override
    public void beginNullableList(int l) {
        ++level;
        writeNullity(false);
        writeListHeader(l);
    }

    @Override
    public void writeListOfNullable(Object... v) {
        writeListHeader(v.length);
        writeNullable(v);
        writeFooter();
    }

    @Override
    public void beginMap(int l) {
        ++level;
        writeMapHeader(l);
    }

    @Override
    public void beginNullableMap(int l) {
        ++level;
        writeNullity(false);
        writeMapHeader(l);
    }

    @Override
    public void end() {
        if (level == 0) {
            throw new IllegalStateException();
        }
        writeFooter();
        --level;
    }

    private void writeListHeader(int l) {
        _writeRLPListHeader();
    }

    private void writeMapHeader(int l) {
        _writeRLPListHeader();
    }

    private void _writeRLPListHeader() {
        os = new ByteArrayBuilder();
        frames.add(os);
    }

    private void _writeRLPListFooter() {
        var prev = os;
        var l = prev.size();
        frames.remove(frames.size() - 1);
        os = frames.get(frames.size() - 1);
        if (l <= 55) {
            os.write(0xc0 + l);
            os.write(prev.array(), 0, prev.size());
        } else if (l <= 0xff) {
            os.write(0xf8);
            os.write(l);
            os.write(prev.array(), 0, prev.size());
        } else if (l <= 0xffff) {
            os.write(0xf9);
            os.write((l >> 8) & 0xff);
            os.write(l & 0xff);
            os.write(prev.array(), 0, prev.size());
        } else if (l <= 0xffffff) {
            os.write(0xfa);
            os.write((l >> 16) & 0xff);
            os.write((l >> 8) & 0xff);
            os.write(l & 0xff);
            os.write(prev.array(), 0, prev.size());
        } else {
            os.write(0xfb);
            os.write((l >> 24) & 0xff);
            os.write((l >> 16) & 0xff);
            os.write((l >> 8) & 0xff);
            os.write(l & 0xff);
            os.write(prev.array(), 0, prev.size());
        }
    }

    private void writeFooter() {
        _writeRLPListFooter();
    }

    private void flush() {
        os.flush();
    }

    @Override
    public byte[] toByteArray() {
        flush();
        return Arrays.copyOfRange(os.array(), 0, os.size());
    }

    private long getTotalWrittenBytes() {
        return os.size();
    }

    public static class ByteArrayBuilder extends OutputStream {
        private static final int INITIAL_CAP = 8;
        private byte[] buf = new byte[INITIAL_CAP];
        private int size;

        private void ensureCap(int req) {
            if (req > buf.length) {
                int newCap = buf.length * 2;
                if (newCap < req) {
                    newCap = req;
                }
                buf = Arrays.copyOf(buf, newCap);
            }
        }

        public void write(int b) {
            ensureCap(size + 1);
            buf[size++] = (byte) b;
        }

        public void write(byte[] b) {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) {
            ensureCap(size + len);
            System.arraycopy(b, off, buf, size, len);
            size += len;
        }

        public void flush() {
        }

        public void close() {
        }

        public byte[] array() {
            return buf;
        }

        public int size() {
            return size;
        }
    }
}
