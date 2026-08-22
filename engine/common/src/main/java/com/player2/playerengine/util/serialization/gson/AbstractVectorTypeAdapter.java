package com.player2.playerengine.util.serialization.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Collection;

public abstract class AbstractVectorTypeAdapter<T> extends TypeAdapter<T> {

    protected abstract Collection<String> getParts(T value);
    protected abstract T fromParts(String[] parts) throws IOException;

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        Collection<String> parts = getParts(value);
        out.value(String.join(",", parts));
    }

    @Override
    public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        String[] parts = value.split(",");
        return fromParts(parts);
    }
}