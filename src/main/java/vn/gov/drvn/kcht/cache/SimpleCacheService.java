package vn.gov.drvn.kcht.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Implementation Cache trong bộ nhớ RAM cho môi trường local development.
 */
@Service
public class SimpleCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(SimpleCacheService.class);

    private final Map<String, Map<String, Object>> cacheStorage = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        Map<String, Object> cache = cacheStorage.get(cacheName);
        if (cache != null && cache.containsKey(key)) {
            Object value = cache.get(key);
            if (type.isInstance(value)) {
                return Optional.of((T) value);
            }
        }
        return Optional.empty();
    }

    @Override
    public <T> void put(String cacheName, String key, T value) {
        cacheStorage.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheName, String key, Class<T> type, Supplier<T> supplier) {
        Optional<T> cached = get(cacheName, key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T computed = supplier.get();
        if (computed != null) {
            put(cacheName, key, computed);
        }
        return computed;
    }

    @Override
    public void evict(String cacheName, String key) {
        Map<String, Object> cache = cacheStorage.get(cacheName);
        if (cache != null) {
            cache.remove(key);
        }
    }

    @Override
    public void clear(String cacheName) {
        cacheStorage.remove(cacheName);
        log.debug("Đã xóa toàn bộ bộ đệm: [{}]", cacheName);
    }
}
