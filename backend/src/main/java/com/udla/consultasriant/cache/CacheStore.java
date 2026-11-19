package com.udla.consultasriant.cache;

import java.util.Optional;

public interface CacheStore {

    Optional<String> get(String key);

    void set(String key, String value, long ttlSeconds);

    boolean isCloudConnected();

    String getProviderName();
}
