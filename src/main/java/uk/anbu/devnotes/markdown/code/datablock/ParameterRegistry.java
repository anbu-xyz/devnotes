package uk.anbu.devnotes.markdown.code.datablock;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParameterRegistry {
    private final Map<String, Object> params = new ConcurrentHashMap<>();

    public void putAll(Map<String, Object> newParams) {
        if (newParams == null) return;
        params.putAll(newParams);
    }

    public void put(String name, Object value) {
        if (name == null) return;
        params.put(name, value);
    }

    public Object get(String name) {
        return params.get(name);
    }

    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(params);
    }

    public void clear() {
        params.clear();
    }
}
