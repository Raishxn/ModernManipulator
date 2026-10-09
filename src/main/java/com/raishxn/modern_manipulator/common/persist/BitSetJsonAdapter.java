package com.raishxn.modern_manipulator.common.persist;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.BitSet;

/**
 * Stores a bit set as a hex string of its long words.
 */
public class BitSetJsonAdapter implements JsonSerializer<BitSet>, JsonDeserializer<BitSet> {

    @Override
    public BitSet deserialize(JsonElement json, Type typeOfT,
                              JsonDeserializationContext context) throws JsonParseException {
        String s = json.getAsString();

        if (s.isEmpty()) return new BitSet();

        String[] words = s.split(",");
        long[] longs = new long[words.length];

        for (int i = 0; i < words.length; i++) {
            longs[i] = Long.parseUnsignedLong(words[i], 16);
        }

        return BitSet.valueOf(longs);
    }

    @Override
    public JsonElement serialize(BitSet src, Type typeOfSrc, JsonSerializationContext context) {
        StringBuilder sb = new StringBuilder();

        for (long l : src.toLongArray()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(Long.toHexString(l));
        }

        return new JsonPrimitive(sb.toString());
    }
}
