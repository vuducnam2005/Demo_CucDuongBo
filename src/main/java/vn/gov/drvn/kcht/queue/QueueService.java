package vn.gov.drvn.kcht.queue;

import java.util.function.Consumer;

/**
 * Interface trừu tượng hóa dịch vụ hàng đợi (Message Queue).
 * Mặc định sử dụng InMemoryQueueService ở môi trường local development,
 * và sẵn sàng thay thế bằng RabbitMQ hoặc Kafka khi scale microservices.
 */
public interface QueueService {

    /**
     * Đẩy tin nhắn / sự kiện vào hàng đợi.
     *
     * @param queueName Tên hàng đợi hoặc topic
     * @param message Nội dung sự kiện / thông điệp
     * @param <T> Kiểu dữ liệu
     */
    <T> void publish(String queueName, T message);

    /**
     * Đăng ký người nhận (Consumer / Subscriber) xử lý tin nhắn từ hàng đợi.
     *
     * @param queueName Tên hàng đợi hoặc topic
     * @param consumer Hàm xử lý tin nhắn
     * @param <T> Kiểu dữ liệu
     */
    <T> void subscribe(String queueName, Consumer<T> consumer);
}
