package vn.gov.drvn.kcht.cache;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Interface trừu tượng hóa dịch vụ bộ nhớ đệm (Cache).
 * Mặc định sử dụng ConcurrentHashMap trong bộ nhớ RAM,
 * và sẵn sàng thay thế bằng Redis Cluster khi scale tải cao.
 */
public interface CacheService {

    <T> Optional<T> get(String cacheName, String key, Class<T> type);

    <T> void put(String cacheName, String key, T value);

    <T> T getOrCompute(String cacheName, String key, Class<T> type, Supplier<T> supplier);

    void evict(String cacheName, String key);

    void clear(String cacheName);
}
