package pl.macieserafin.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.exception.SearchValidationException;
import pl.macieserafin.model.ApiError;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(SearchValidationException.class)
    public ResponseEntity<ApiError> handleSearchError(SearchValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_SEARCH", exception.getMessage()));
    }

    @ExceptionHandler(PkpApiException.class)
    public ResponseEntity<ApiError> handlePkpError(PkpApiException exception) {
        var status = switch (exception.reason()) {
            case MISSING_KEY, RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case ACCESS_DENIED, UPSTREAM_ERROR, INVALID_RESPONSE, TLS_ERROR, CONNECTION_ERROR ->
                    HttpStatus.BAD_GATEWAY;
        };
        var message = switch (exception.reason()) {
            case MISSING_KEY -> "PKP API is not configured.";
            case ACCESS_DENIED -> "PKP access denied.";
            case RATE_LIMITED -> "PKP request limit reached. Try again later.";
            case UPSTREAM_ERROR -> "PKP service error.";
            case INVALID_RESPONSE -> "Invalid PKP response.";
            case TIMEOUT -> "PKP request timed out.";
            case TLS_ERROR -> "PKP secure connection failed.";
            case CONNECTION_ERROR -> "PKP service is unavailable.";
        };
        return ResponseEntity.status(status).body(new ApiError(exception.reason().name(), message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var httpStatus = HttpStatus.resolve(status.value());
        String code = httpStatus == null ? "REQUEST_ERROR" : httpStatus.name();
        String message = switch (status.value()) {
            case 400 -> "Invalid request parameters.";
            case 404 -> "Endpoint not found.";
            case 405 -> "Method not allowed.";
            default -> "Request could not be processed.";
        };
        return new ResponseEntity<>(new ApiError(code, message), headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
