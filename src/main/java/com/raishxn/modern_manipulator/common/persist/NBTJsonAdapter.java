package com.raishxn.modern_manipulator.common.persist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Stores compound tags as SNBT strings so that they are lossless.
 */
public class NBTJsonAdapter implements JsonSerializer<CompoundTag>, JsonDeserializer<CompoundTag> {

    @Override
    public CompoundTag deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            return TagParser.parseTag(json.getAsString());
        } catch (Exception e) {
            throw new JsonParseException("Could not parse NBT: " + json, e);
        }
    }

    @Override
    public JsonElement serialize(CompoundTag src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}
