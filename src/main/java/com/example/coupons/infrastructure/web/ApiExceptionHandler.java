package com.example.coupons.infrastructure.web;

import com.example.coupons.domain.exception.AlreadyRedeemedException;
import com.example.coupons.domain.exception.CountryNotAllowedException;
import com.example.coupons.domain.exception.CountryNotDeterminedException;
import com.example.coupons.domain.exception.CouponNotFoundException;
import com.example.coupons.domain.exception.DomainValidationException;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.exception.UsageLimitReachedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CouponNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CouponNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "Coupon not found", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateCouponCodeException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateCouponCodeException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE_CODE", "Coupon code already exists", ex.getMessage(), request);
    }

    @ExceptionHandler(UsageLimitReachedException.class)
    ResponseEntity<ProblemDetail> handleUsageLimitReached(UsageLimitReachedException ex,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "USAGE_LIMIT_REACHED", "Coupon usage limit reached",
                ex.getMessage(), request);
    }

    @ExceptionHandler(AlreadyRedeemedException.class)
    ResponseEntity<ProblemDetail> handleAlreadyRedeemed(AlreadyRedeemedException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "ALREADY_REDEEMED", "Coupon already redeemed by this user",
                ex.getMessage(), request);
    }

    @ExceptionHandler(CountryNotAllowedException.class)
    ResponseEntity<ProblemDetail> handleCountryNotAllowed(CountryNotAllowedException ex,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "COUNTRY_NOT_ALLOWED", "Coupon not available from your country",
                ex.getMessage(), request);
    }

    @ExceptionHandler(CountryNotDeterminedException.class)
    ResponseEntity<ProblemDetail> handleCountryNotDetermined(CountryNotDeterminedException ex,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_DETERMINED",
                "Could not determine the caller's country", ex.getMessage(), request);
    }

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ProblemDetail> handleDomainValidation(DomainValidationException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        logger.error("Unhandled exception on " + request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error",
                "An unexpected error occurred.", request);
    }

    /** Bean-validation failure on a request body — keep the field-level {@code errors} array. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        body.setTitle("Request validation failed");
        decorate(body, "VALIDATION_ERROR", request);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response.getBody() instanceof ProblemDetail problemDetail && !hasCode(problemDetail)) {
            decorate(problemDetail, codeFor(statusCode, ex), request);
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problemDetail) {
        return problemDetail.getProperties() != null && problemDetail.getProperties().containsKey("code");
    }

    private static String codeFor(HttpStatusCode status, Exception ex) {
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
            return "MALFORMED_REQUEST";
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "NOT_FOUND";
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return "METHOD_NOT_ALLOWED";
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return "UNSUPPORTED_MEDIA_TYPE";
        }
        return "BAD_REQUEST";
    }

    private static void decorate(ProblemDetail body, String code, WebRequest request) {
        body.setProperty("code", code);
        if (request instanceof ServletWebRequest servletRequest) {
            body.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String title,
                                                        String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("code", code);
        body.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(body);
    }
}
