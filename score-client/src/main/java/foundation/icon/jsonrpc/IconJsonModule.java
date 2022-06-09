/*
 * Copyright 2021 ICON Foundation
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

package foundation.icon.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.function.Function;

public class IconJsonModule extends SimpleModule {
    public final static Version VERSION = VersionUtil.parseVersion(
            "0.1.0", "foundation.icon", "javaee-score-client"
    );

    public static final String BOOLEAN_TRUE = "0x1";
    public static final String BOOLEAN_FALSE = "0x0";
    public static final String HEX_PREFIX = "0x";
    public static final String NEG_HEX_PREFIX = "-0x";
    private final boolean isIncludeNonNull;

    public static final char[] HEX_CODES = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder r = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            r.append(HEX_CODES[(b >> 4) & 0xF]);
            r.append(HEX_CODES[(b & 0xF)]);
        }
        return r.toString();
    }

    public static byte[] hexToBytes(String hexString) {
        if (hexString == null) {
            return null;
        }
        if (hexString.length() % 2 > 0) {
            throw new IllegalArgumentException("hex cannot has odd length");
        }
        int l = hexString.length()/2;
        int j = 0;
        byte[] bytes = new byte[l];
        for (int i = 0; i < l; i++) {
            bytes[i] = (byte)((Character.digit(hexString.charAt(j++), 16) << 4) |
                    Character.digit(hexString.charAt(j++), 16) & 0xFF);
        }
        return bytes;
    }

    public IconJsonModule() {
        super(VERSION);
        this.isIncludeNonNull = true;
        init();
    }

    public IconJsonModule(boolean isIncludeNonNull) {
        super(VERSION);
        this.isIncludeNonNull = isIncludeNonNull;
        init();
    }

    private void init() {
        addSerializer(char.class, CharSerializer.CHAR);
        addSerializer(Character.class, CharSerializer.CHAR);
        addSerializer(byte.class, NumberSerializer.BYTE);
        addSerializer(Byte.class, NumberSerializer.BYTE);
        addSerializer(long.class, NumberSerializer.LONG);
        addSerializer(Long.class, NumberSerializer.LONG);
        addSerializer(int.class, NumberSerializer.INTEGER);
        addSerializer(Integer.class, NumberSerializer.INTEGER);
        addSerializer(short.class, NumberSerializer.SHORT);
        addSerializer(Short.class, NumberSerializer.SHORT);
        addSerializer(BigInteger.class, NumberSerializer.BIG_INTEGER);
        addSerializer(boolean.class, BooleanSerializer.BOOLEAN);
        addSerializer(Boolean.class, BooleanSerializer.BOOLEAN);
        addSerializer(byte[].class, ByteArraySerializer.BYTE_ARRAY);
        addSerializer(score.Address.class, AddressSerializer.SCORE_ADDRESS);
//        addSerializer(foundation.icon.icx.data.Address.class, AddressSerializer.SDK_ADDRESS);

        addDeserializer(char.class, CharDeserializer.CHAR);
        addDeserializer(Character.class, CharDeserializer.CHAR);
        addDeserializer(byte.class, NumberDeserializer.BYTE);
        addDeserializer(Byte.class, NumberDeserializer.BYTE);
        addDeserializer(long.class, NumberDeserializer.LONG);
        addDeserializer(Long.class, NumberDeserializer.LONG);
        addDeserializer(int.class, NumberDeserializer.INTEGER);
        addDeserializer(Integer.class, NumberDeserializer.INTEGER);
        addDeserializer(short.class, NumberDeserializer.SHORT);
        addDeserializer(Short.class, NumberDeserializer.SHORT);
        addDeserializer(BigInteger.class, NumberDeserializer.BIG_INTEGER);
        addDeserializer(boolean.class, BooleanDeserializer.BOOLEAN);
        addDeserializer(Boolean.class, BooleanDeserializer.BOOLEAN);
        addDeserializer(byte[].class, ByteArrayDeserializer.BYTE_ARRAY);
        addDeserializer(score.Address.class, AddressDeserializer.SCORE_ADDRESS);
//        addDeserializer(foundation.icon.icx.data.Address.class, AddressDeserializer.SDK_ADDRESS);
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        if (isIncludeNonNull) {
            JsonInclude.Value value = JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL);
            context.configOverride(Long.class).setInclude(value);
            context.configOverride(Integer.class).setInclude(value);
            context.configOverride(Short.class).setInclude(value);
            context.configOverride(BigInteger.class).setInclude(value);
            context.configOverride(Boolean.class).setInclude(value);
            context.configOverride(byte[].class).setInclude(value);
        }
    }

    public static class NumberSerializer<T extends Number> extends JsonSerializer<T> implements Converter<T, String> {
        public static final NumberSerializer<Byte> BYTE = new NumberSerializer<>();
        public static final NumberSerializer<Short> SHORT = new NumberSerializer<>();
        public static final NumberSerializer<Integer> INTEGER = new NumberSerializer<>();
        public static final NumberSerializer<Long> LONG = new NumberSerializer<>();
        public static final NumberSerializer<BigInteger> BIG_INTEGER = new NumberSerializer<>();

        @Override
        public String convert(T t) {
            BigInteger bi;
            if (t instanceof BigInteger) {
                bi = (BigInteger) t;
            } else {
                bi = BigInteger.valueOf(t.longValue());
            }
            String prefix = (bi.signum() == -1) ? NEG_HEX_PREFIX : HEX_PREFIX;
            return prefix + bi.abs().toString(16);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(new TypeReference<T>(){});
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(convert(value));
        }
    }

    public static class CharSerializer extends JsonSerializer<Character> implements Converter<Character, String> {
        public static final CharSerializer CHAR = new CharSerializer();

        @Override
        public String convert(Character value) {
            return NumberSerializer.INTEGER.convert((int)value);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(Character.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public void serialize(Character value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(convert(value));
        }
    }

    public static class BooleanSerializer extends JsonSerializer<Boolean> implements Converter<Boolean, String> {
        public static final BooleanSerializer BOOLEAN = new BooleanSerializer();

        @Override
        public String convert(Boolean value) {
            return value ? BOOLEAN_TRUE : BOOLEAN_FALSE;
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(Boolean.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public void serialize(Boolean value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeString(convert(value));
            }
        }
    }

    public static class ByteArraySerializer extends JsonSerializer<byte[]> implements Converter<byte[], String> {
        public static final ByteArraySerializer BYTE_ARRAY = new ByteArraySerializer();

        @Override
        public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(convert(value));
        }

        @Override
        public String convert(byte[] value) {
            return HEX_PREFIX + bytesToHex(value);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructArrayType(byte.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }
    }

    public static class AddressSerializer<T> extends JsonSerializer<T> implements Converter<T, String> {
        public static final AddressSerializer<score.Address> SCORE_ADDRESS = new AddressSerializer<>();
        public static final AddressSerializer<foundation.icon.icx.data.Address> SDK_ADDRESS = new AddressSerializer<>();

        @Override
        public String convert(T value) {
            return value.toString();
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(new TypeReference<T>(){});
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public void serialize(T address, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(convert(address));
        }
    }

    public static class NumberDeserializer<T extends Number> extends JsonDeserializer<T> implements Converter<String, T>{
        public static final NumberDeserializer<Byte> BYTE = new NumberDeserializer<>(BigInteger::byteValue);
        public static final NumberDeserializer<Short> SHORT = new NumberDeserializer<>(BigInteger::shortValue);
        public static final NumberDeserializer<Integer> INTEGER = new NumberDeserializer<>(BigInteger::intValue);
        public static final NumberDeserializer<Long> LONG = new NumberDeserializer<>(BigInteger::longValue);
        public static final NumberDeserializer<BigInteger> BIG_INTEGER = new NumberDeserializer<>(bi -> bi);

        private final Function<BigInteger, T> parseFunc;

        public NumberDeserializer(Function<BigInteger, T> parseFunc) {
            this.parseFunc = parseFunc;
        }

        @Override
        public T convert(String s) {
            if (s.startsWith(HEX_PREFIX)) {
                return parseFunc.apply(new BigInteger(s.substring(2), 16));
            } else if (s.startsWith(NEG_HEX_PREFIX)) {
                return parseFunc.apply(new BigInteger(s.substring(3), 16).negate());
            } else {
//                throw new IllegalArgumentException(String.format("invalid prefix loc:%s", p.getCurrentLocation().toString()));
                return parseFunc.apply(new BigInteger(s, 16));
            }
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(new TypeReference<T>(){});
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            if (p.currentToken().isNumeric()) {
                return parseFunc.apply(p.getBigIntegerValue());
            } else {
                return convert(p.getValueAsString());
            }
        }
    }

    public static class CharDeserializer extends JsonDeserializer<Character> implements Converter<String, Character> {
        public static final CharDeserializer CHAR = new CharDeserializer();

        @Override
        public Character convert(String value) {
            return (char)NumberDeserializer.INTEGER.convert(value).intValue();
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(Boolean.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public Character deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            if (p.currentToken().isNumeric()) {
                return (char)p.getIntValue();
            } else {
                return convert(p.getValueAsString());
            }
        }
    }

    public static class BooleanDeserializer extends JsonDeserializer<Boolean> implements Converter<String, Boolean> {
        public static final BooleanDeserializer BOOLEAN = new BooleanDeserializer();

        @Override
        public Boolean convert(String value) {
            if (BOOLEAN_TRUE.equals(value)) {
                return Boolean.TRUE;
            } else if (BOOLEAN_FALSE.equals(value)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("invalid value:"+value);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(Boolean.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            try{
                return convert(p.getValueAsString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("fail to deserialize loc:%s err:%s",
                                p.getCurrentLocation().toString(), e.getMessage()),e);
            }
        }
    }

    public static class ByteArrayDeserializer extends JsonDeserializer<byte[]> implements Converter<String, byte[]>{
        public static final ByteArrayDeserializer BYTE_ARRAY = new ByteArrayDeserializer();

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            try{
                return convert(p.getValueAsString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("fail to deserialize loc:%s err:%s",
                                p.getCurrentLocation().toString(), e.getMessage()),e);
            }
        }

        @Override
        public byte[] convert(String s) {
            if (s.length() % 2 == 0) {
                if (s.startsWith(HEX_PREFIX)) {
                    s = s.substring(2);
                }
                return hexToBytes(s);
            } else {
                throw new IllegalArgumentException("hex string length must be even");
            }
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructArrayType(byte.class);
        }
    }

    public static class AddressDeserializer<T> extends JsonDeserializer<T> implements Converter<String, T> {
        public static final AddressDeserializer<score.Address> SCORE_ADDRESS = new AddressDeserializer<>(Address::new);
        public static final AddressDeserializer<foundation.icon.icx.data.Address> SDK_ADDRESS = new AddressDeserializer<>(foundation.icon.icx.data.Address::new);

        private final Function<String, T> parseFunc;

        public AddressDeserializer(Function<String, T> parseFunc) {
            this.parseFunc = parseFunc;
        }

        @Override
        public T convert(String value) {
            return parseFunc.apply(value);
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(new TypeReference<T>(){});
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return convert(p.getValueAsString());
        }
    }

}
