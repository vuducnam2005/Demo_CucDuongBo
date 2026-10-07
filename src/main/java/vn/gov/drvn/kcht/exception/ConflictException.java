package vn.gov.drvn.kcht.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ngoại lệ phát sinh khi xảy ra xung đột dữ liệu hoặc vi phạm Khóa lạc quan (Optimistic Locking Failure).
 * Tương ứng với mã HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
