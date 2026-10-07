package vn.gov.drvn.kcht.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình bộ nhớ đệm (Cache) cho Giai đoạn 13 - Hiệu năng và Vận hành.
 * Lưu trữ in-memory các siêu dữ liệu truy cập thường xuyên (Dashboard aggregations, Metadata, Registry).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(
                "dashboardSummary",
                "dashboardBranchStats",
                "dashboardTopDatasets",
                "dashboardRoadSigns",
                "dashboardRoadLengths",
                "datasetMetadata",
                "referenceCatalogs"
        ));
        return cacheManager;
    }
}
