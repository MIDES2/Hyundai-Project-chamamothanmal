package io.chamamothanmal.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(Map<String, String> error) { }

    static ErrorBody body(String code, String message) {
        return new ErrorBody(Map.of("code", code, "message", message));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handle(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(body(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooLarge() {
        return ResponseEntity.status(413).body(body("AUDIO_TOO_LARGE", "Upload exceeds the configured limit."));
    }

    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
        MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class, MultipartException.class})
    ResponseEntity<ErrorBody> invalid(Exception ignored) {
        // Never echo tokens, multipart content, coordinates, or rejected values.
        return ResponseEntity.badRequest().body(body("INVALID_REQUEST", "Check required fields, types and file format."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body("INTERNAL_ERROR", "The request could not be completed."));
    }
}
