package com.raishxn.modern_manipulator.common.persist;

import net.minecraft.core.Direction;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class DirectionJsonAdapter implements JsonSerializer<Direction>, JsonDeserializer<Direction> {

    @Override
    public Direction deserialize(JsonElement json, Type typeOfT,
                                 JsonDeserializationContext context) throws JsonParseException {
        if (json.getAsJsonPrimitive().isNumber()) {
            int i = json.getAsInt();
            return i >= 0 && i < 6 ? Direction.from3DDataValue(i) : null;
        }

        return Direction.byName(json.getAsString());
    }

    @Override
    public JsonElement serialize(Direction src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }
}
