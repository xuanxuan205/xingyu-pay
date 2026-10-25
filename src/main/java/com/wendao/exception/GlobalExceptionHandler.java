package com.wendao.exception;

import com.wendao.model.ResponseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseVO<?> handleApiException(ApiException e) {
        log.warn("API异常 - 错误码: {}, 错误信息: {}", e.getCode(), e.getMessage());
        return ResponseVO.builder()
                .code(e.getCode())
                .msg(e.getMessage())
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseVO<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ResponseVO.builder()
                .code(ApiException.ErrorCode.INVALID_PARAM)
                .msg(e.getMessage())
                .build();
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseVO<?> handleBadRequest(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return ResponseVO.builder()
                .code(ApiException.ErrorCode.INVALID_PARAM)
                .msg("请求参数不正确")
                .build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseVO<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不允许: {}", e.getMessage());
        return ResponseVO.builder()
                .code(HttpStatus.METHOD_NOT_ALLOWED.value())
                .msg("请求方法不允许")
                .build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ResponseVO<?> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("请求类型不支持: {}", e.getMessage());
        return ResponseVO.builder()
                .code(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .msg("请求类型不支持")
                .build();
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseVO<?> handleNotFound(NoHandlerFoundException e) {
        return ResponseVO.builder()
                .code(HttpStatus.NOT_FOUND.value())
                .msg("接口不存在")
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseVO<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseVO.builder()
                .code(ApiException.ErrorCode.SYSTEM_ERROR)
                .msg("系统内部异常")
                .build();
    }
}
