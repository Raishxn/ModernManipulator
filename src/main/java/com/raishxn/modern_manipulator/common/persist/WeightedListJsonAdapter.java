package com.raishxn.modern_manipulator.common.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.data.WeightedSpecList;
import it.unimi.dsi.fastutil.objects.ObjectIntMutablePair;

import java.lang.reflect.Type;

public class WeightedListJsonAdapter implements JsonSerializer<WeightedSpecList>, JsonDeserializer<WeightedSpecList> {

    @Override
    public WeightedSpecList deserialize(JsonElement json, Type typeOfT,
                                        JsonDeserializationContext context) throws JsonParseException {
        WeightedSpecList list = new WeightedSpecList();

        for (JsonElement e : json.getAsJsonArray()) {
            JsonObject obj = e.getAsJsonObject();

            BlockSpec spec = context.deserialize(obj.get("v"), BlockSpec.class);
            int weight = obj.has("w") ? obj.get("w").getAsInt() : 1;

            if (spec != null) list.specs.add(ObjectIntMutablePair.of(spec, weight));
        }

        return list;
    }

    @Override
    public JsonElement serialize(WeightedSpecList src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();

        for (var p : src.specs) {
            JsonObject obj = new JsonObject();

            obj.add("v", context.serialize(p.left(), BlockSpec.class));
            if (p.rightInt() != 1) obj.addProperty("w", p.rightInt());

            array.add(obj);
        }

        return array;
    }
}
