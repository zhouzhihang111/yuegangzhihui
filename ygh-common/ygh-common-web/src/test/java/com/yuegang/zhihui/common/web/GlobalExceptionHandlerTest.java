package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessConflictToHttp409AndStableEnvelope() {
        var request = requestWithTraceId("trace-conflict");

        var response = handler.handleBusinessException(
                new BusinessException(ErrorCode.BUSINESS_CONFLICT, "订单状态不允许取消"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("订单状态不允许取消");
        assertThat(response.getBody().traceId()).isEqualTo("trace-conflict");
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromClient() {
        var request = requestWithTraceId("trace-error");

        var response = handler.handleUnexpectedException(new IllegalStateException("database password leak"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("系统内部错误");
        assertThat(response.getBody().message()).doesNotContain("password");
    }

    @Test
    void unreadableJsonIsASecretFreeValidationFailure() {
        var response = handler.handleUnreadableMessage(null, requestWithTraceId("trace-json"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().traceId()).isEqualTo("trace-json");
        assertThat(response.getBody().data()).containsExactly(
                FieldValidationError.sanitized("body", "请求体格式不合法"));
    }

    @Test
    void standardMvcProtocolFailuresRemainSanitized4xxResponses() {
        var request = requestWithTraceId("trace-protocol");
        var method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT", List.of("POST")), request);
        assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(method.getHeaders().getAllow()).containsExactly(org.springframework.http.HttpMethod.POST);
        assertThat(method.getBody()).isNotNull();
        assertThat(method.getBody().code()).isEqualTo("VALIDATION_ERROR");

        var media = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(
                        MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), request);
        assertThat(media.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(media.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);

        var binding = handler.handleRequestBindingFailure(
                new ServletRequestBindingException("secret-internal-detail"), request);
        assertThat(binding.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(binding.getBody()).isNotNull();
        assertThat(binding.getBody().toString()).doesNotContain("secret-internal-detail");

        var unacceptable = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);
        assertThat(unacceptable.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(unacceptable.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);

        var missing = handler.handleResourceNotFound(
                new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/private/secret", "/private/secret"), request);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(missing.getBody().toString()).doesNotContain("/private/secret");
    }

    @Test
    void validationFailureReturnsFieldErrorsInsideTheStandardEnvelope() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "registrationRequest");
        bindingResult.addError(new FieldError(
                "registrationRequest",
                "password",
                "PlainSecret-123",
                false,
                null,
                null,
                "密码长度必须不少于 12 位"));
        bindingResult.addError(new FieldError(
                "registrationRequest",
                "displayName",
                "",
                false,
                null,
                null,
                "显示名称不能为空"));
        var exception = new MethodArgumentNotValidException(validationMethodParameter(), bindingResult);

        var response = handler.handleMethodArgumentNotValid(
                exception, requestWithTraceId("trace-validation"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.VALIDATION_ERROR.defaultMessage());
        assertThat(response.getBody().traceId()).isEqualTo("trace-validation");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::field)
                .containsExactly("password", "displayName");
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::message)
                .containsExactly("密码长度必须不少于 12 位", "显示名称不能为空");
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::rejectedValue)
                .containsOnlyNulls();
        assertThat(response.getBody().data().toString()).doesNotContain("PlainSecret-123");
    }

    @Test
    void authenticationFailureMapsTo401WithoutLeakingBearerOrCause() {
        assertSanitizedBusinessFailure(
                ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED,
                "Bearer eyJ.secret-token caused authentication failure");
    }

    @Test
    void permissionFailureMapsTo403WithoutLeakingPolicyInternals() {
        assertSanitizedBusinessFailure(
                ErrorCode.PERMISSION_DENIED,
                HttpStatus.FORBIDDEN,
                "internal policy admin:wallet:write denied subject user-1");
    }

    @Test
    void rateLimitFailureMapsTo429WithoutLeakingLimiterInternals() {
        var response = assertSanitizedBusinessFailure(
                ErrorCode.RATE_LIMITED,
                HttpStatus.TOO_MANY_REQUESTS,
                "redis limiter key ygh:dev:secret:user-1 exhausted");

        assertThat(response.getHeaders().getFirst("Retry-After"))
                .matches("[1-9][0-9]*");
    }

    @Test
    void dependencyFailureMapsTo503WithoutLeakingUpstreamDetails() {
        assertSanitizedBusinessFailure(
                ErrorCode.DEPENDENCY_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "mysql://root:password@192.168.154.129 refused connection");
    }

    @Test
    void handlerMethodValidationReturnsSanitizedParameterLocation() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod(
                "methodValidationTarget", String.class);
        var methodParameter = new MethodParameter(method, 0);
        var error = new DefaultMessageSourceResolvable(
                new String[]{"NotBlank.accessToken"}, null, "访问令牌不能为空");
        var parameterResult = new ParameterValidationResult(
                methodParameter,
                "Bearer internal-secret-token",
                List.of(error),
                null,
                null,
                null,
                (resolvable, sourceType) -> null);
        var validationResult = MethodValidationResult.create(
                this, method, List.of(parameterResult));
        var exception = new HandlerMethodValidationException(validationResult);

        var response = handler.handleHandlerMethodValidation(
                exception, requestWithTraceId("trace-method-validation"));

        assertValidationErrorResponse(response, "trace-method-validation", "accessToken", "访问令牌不能为空");
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::rejectedValue)
                .containsOnlyNulls();
        assertThat(response.getBody().data().toString()).doesNotContain("internal-secret-token");
    }

    @Test
    void constraintViolationReturnsSanitizedPropertyPath() {
        ConstraintViolation<Object> violation = constraintViolation(
                "wallet.topUp.amount", "金额格式不正确", "999999-secret-value");
        var exception = new ConstraintViolationException(Set.of(violation));

        var response = handler.handleConstraintViolation(
                exception, requestWithTraceId("trace-constraint-validation"));

        assertValidationErrorResponse(
                response,
                "trace-constraint-validation",
                "wallet.topUp.amount",
                "金额格式不正确");
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::rejectedValue)
                .containsOnlyNulls();
        assertThat(response.getBody().data().toString()).doesNotContain("999999-secret-value");
    }

    private ResponseEntity<ApiResponse<Void>> assertSanitizedBusinessFailure(
            ErrorCode errorCode, HttpStatus expectedStatus, String sensitiveInternalDetail) {
        var traceId = "trace-" + errorCode.code().toLowerCase();

        var response = handler.handleBusinessException(
                new BusinessException(errorCode, sensitiveInternalDetail), requestWithTraceId(traceId));

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(errorCode.code());
        assertThat(response.getBody().message()).isEqualTo(errorCode.defaultMessage());
        assertThat(response.getBody().message()).doesNotContain(sensitiveInternalDetail);
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().traceId()).isEqualTo(traceId);
        assertThat(response.getBody().timestamp()).isNotNull();
        return response;
    }

    private void assertValidationErrorResponse(
            ResponseEntity<ApiResponse<List<FieldValidationError>>> response,
            String traceId,
            String field,
            String message) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR.code());
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.VALIDATION_ERROR.defaultMessage());
        assertThat(response.getBody().traceId()).isEqualTo(traceId);
        assertThat(response.getBody().data())
                .extracting(FieldValidationError::field, FieldValidationError::message)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(field, message));
    }

    @SuppressWarnings("unchecked")
    private ConstraintViolation<Object> constraintViolation(
            String propertyPath, String message, Object invalidValue) {
        var path = new Path() {
            @Override
            public Iterator<Node> iterator() {
                return List.<Node>of().iterator();
            }

            @Override
            public String toString() {
                return propertyPath;
            }
        };
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(
                ConstraintViolation.class.getClassLoader(),
                new Class<?>[]{ConstraintViolation.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMessage", "getMessageTemplate" -> message;
                    case "getPropertyPath" -> path;
                    case "getInvalidValue" -> invalidValue;
                    case "getRootBeanClass" -> Object.class;
                    case "toString" -> propertyPath + ": " + message;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }

    private MethodParameter validationMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod(
                "validationTarget", Object.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void validationTarget(Object request) {
        // Reflection-only method used to construct MethodArgumentNotValidException.
    }

    @SuppressWarnings("unused")
    private void methodValidationTarget(String accessToken) {
        // Reflection-only method used to construct HandlerMethodValidationException.
    }

    private MockHttpServletRequest requestWithTraceId(String traceId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, traceId);
        return request;
    }
}
