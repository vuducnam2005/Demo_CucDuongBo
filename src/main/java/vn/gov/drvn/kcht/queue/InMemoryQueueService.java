package vn.gov.drvn.kcht.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Implementation hàng đợi trong bộ nhớ RAM phục vụ môi trường cục bộ,
 * sử dụng Virtual Threads (Java 21) để thực thi bất đồng bộ an toàn.
 */
@Service
public class InMemoryQueueService implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryQueueService.class);

    private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void publish(String queueName, T message) {
        log.debug("Đẩy thông điệp vào hàng đợi [{}]: {}", queueName, message);
        List<Consumer<Object>> consumers = subscribers.get(queueName);
        if (consumers != null && !consumers.isEmpty()) {
            for (Consumer<Object> consumer : consumers) {
                executor.submit(() -> {
                    try {
                        consumer.accept(message);
                    } catch (Exception e) {
                        log.error("Lỗi khi xử lý thông điệp từ hàng đợi [{}]: {}", queueName, e.getMessage(), e);
                    }
                });
            }
        } else {
            log.debug("Chưa có consumer nào đăng ký nhận tin từ hàng đợi [{}]", queueName);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String queueName, Consumer<T> consumer) {
        subscribers.computeIfAbsent(queueName, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) consumer);
        log.info("Đã đăng ký consumer cho hàng đợi: [{}]", queueName);
    }
}
