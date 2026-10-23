package com.shopping.web.handler;

import com.shopping.common.exception.BizException;
import com.shopping.common.result.Result;
import com.shopping.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 设计原则：
 * <ol>
 *   <li><b>业务异常</b>（{@link BizException}）记 warn，不打堆栈——可预期，不是 bug；</li>
 *   <li><b>参数校验失败</b>返回具体是哪个字段错了，方便前端定位；</li>
 *   <li><b>唯一索引冲突</b>单独处理——这是本项目幂等设计的最后一道防线，
 *       撞到了说明并发重复请求，应该降级成友好提示而不是 500；</li>
 *   <li><b>未知异常</b>记 error 并打堆栈，但<b>不把堆栈信息返回给前端</b>，
 *       避免泄漏内部实现细节（表名、类名、SQL）。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，warn 级别，不打堆栈 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 | uri={} | code={} | msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.failure(e.getCode(), e.getMessage());
    }

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | {}", message);
        return Result.failure(ResultCode.BAD_REQUEST, message);
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 | {}", message);
        return Result.failure(ResultCode.BAD_REQUEST, message);
    }

    /** 方法参数上的 @Validated 校验失败（如 @PathVariable @Min(1)） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败 | {}", message);
        return Result.failure(ResultCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.failure(ResultCode.BAD_REQUEST, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败 | {}", e.getMessage());
        return Result.failure(ResultCode.BAD_REQUEST, "请求体格式有误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.failure(ResultCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 唯一索引冲突。
     * <p>
     * 本项目的幂等依赖数据库唯一索引兜底（库存流水、秒杀记录、支付回调日志），
     * 所以并发重复请求最终会撞到这里。它不是「系统错误」，而是「重复请求」，
     * 因此返回 409/幂等提示而不是 500。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突（重复请求被数据库拦下）| {}", e.getMostSpecificCause().getMessage());
        return Result.failure(ResultCode.IDEMPOTENT_REPEAT);
    }

    /**
     * 权限不足：<b>原样抛出</b>，交给 Spring Security 的
     * {@code ExceptionTranslationFilter} → {@code RestAccessDeniedHandler} 处理。
     * <p>
     * <b>为什么必须显式声明这个处理器（本节最容易踩的坑）：</b>
     * {@code @PreAuthorize} 抛出的 {@code AccessDeniedException} 是在 Controller
     * 方法被调用时抛出的，它会先进入 {@code @RestControllerAdvice} 的异常解析链。
     * 而本类下面有一个宽泛的 {@code @ExceptionHandler(Exception.class)}，
     * 如果没有更精确的处理器，它就<b>会把 403 吞掉并返回 500</b>——
     * 表现为「无权限的接口返回系统繁忙」，而日志里只有一条看不出原因的 500。
     * <p>
     * 抛出去之后，HTTP 状态码会是 403、响应体格式与其它错误一致，
     * 且「401/403 长什么样」在整个系统里只有一处定义。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) {
        throw e;
    }

    /** 兜底：未知异常记 error + 堆栈，但不对外暴露细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常 | uri={} | method={}", request.getRequestURI(), request.getMethod(), e);
        return Result.failure(ResultCode.INTERNAL_ERROR);
    }

    private static String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
