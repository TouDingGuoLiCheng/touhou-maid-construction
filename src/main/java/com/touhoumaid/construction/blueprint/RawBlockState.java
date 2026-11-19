package com.touhoumaid.construction.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Block id + property map as stored in a litematic palette entry (before registry resolve).
 */
public final class RawBlockState {
    private final String name;
    private final Map<String, String> properties;

    public RawBlockState(String name, Map<String, String> properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public String name() {
        return name;
    }

    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return name;
        }
        return name + properties;
    }
}
