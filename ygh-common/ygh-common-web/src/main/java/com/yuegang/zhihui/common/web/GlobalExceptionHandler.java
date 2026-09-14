package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates expected failures into stable API responses and prevents internal
 * exception details from leaking to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Log LOGGER = LogFactory.getLog(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        var status = statusFor(exception.errorCode());
        var message = externalMessage(exception);
        var body = ApiResponse.<Void>failure(
                exception.errorCode(), message, TraceIdResolver.resolve(request));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (exception.errorCode() == ErrorCode.RATE_LIMITED) {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> FieldValidationError.sanitized(
                        error.getField(),
                        resolveValidationMessage(error.getDefaultMessage())))
                .toList();
        var body = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                fieldErrors,
                TraceIdResolver.resolve(request));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> FieldValidationError.sanitized(
                                resolveParameterName(result.getMethodParameter()),
                                resolveValidationMessage(error.getDefaultMessage()))))
                .toList();
        return validationFailure(fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldValidationError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> FieldValidationError.sanitized(
                        violation.getPropertyPath().toString(),
                        resolveValidationMessage(violation.getMessage())))
                .toList();
        return validationFailure(fieldErrors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return validationFailure(
                List.of(FieldValidationError.sanitized("body", "请求体格式不合法")), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return protocolFailure(
                exception.getStatusCode(), exception.getHeaders(), "method", "请求方法不受支持", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return protocolFailure(
                exception.getStatusCode(), exception.getHeaders(), "contentType", "媒体类型不受支持", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request
    ) {
        return protocolFailure(
                exception.getStatusCode(), exception.getHeaders(), "accept", "无法生成可接受的响应类型", request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            Exception exception,
            HttpServletRequest request
    ) {
        var errorResponse = (ErrorResponse) exception;
        var body = ApiResponse.<Void>failure(
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(),
                TraceIdResolver.resolve(request));
        return ResponseEntity.status(errorResponse.getStatusCode())
                .headers(errorResponse.getHeaders()).body(body);
    }

    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<List<FieldValidationError>>> handleRequestBindingFailure(
            Exception exception,
            HttpServletRequest request
    ) {
        return validationFailure(
                List.of(FieldValidationError.sanitized("request", "请求参数不合法")), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        var traceId = TraceIdResolver.resolve(request);
        // Do not log exception messages here because they may contain credentials
        // or personal data. Service-specific reporters can attach a sanitized cause.
        LOGGER.error("Unexpected exception, traceId=" + traceId + ", type=" + exception.getClass().getName());
        var body = ApiResponse.<Void>failure(
                ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BUSINESS_CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SUCCESS -> HttpStatus.OK;
        };
    }

    private String externalMessage(BusinessException exception) {
        return switch (exception.errorCode()) {
            case UNAUTHENTICATED, PERMISSION_DENIED, RATE_LIMITED,
                    DEPENDENCY_UNAVAILABLE, INTERNAL_ERROR -> exception.errorCode().defaultMessage();
            default -> exception.getMessage();
        };
    }

    private String resolveValidationMessage(String message) {
        return message == null || message.isBlank() ? "字段值不合法" : message;
    }

    private String resolveParameterName(org.springframework.core.MethodParameter parameter) {
        String parameterName = parameter.getParameterName();
        return parameterName == null || parameterName.isBlank()
                ? "arg" + parameter.getParameterIndex()
                : parameterName;
    }

    private ResponseEntity<ApiResponse<List<FieldValidationError>>> validationFailure(
            List<FieldValidationError> fieldErrors,
            HttpServletRequest request
    ) {
        var body = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                fieldErrors,
                TraceIdResolver.resolve(request));
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ApiResponse<List<FieldValidationError>>> protocolFailure(
            org.springframework.http.HttpStatusCode status,
            HttpHeaders headers,
            String field,
            String message,
            HttpServletRequest request
    ) {
        var body = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(FieldValidationError.sanitized(field, message)),
                TraceIdResolver.resolve(request));
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
