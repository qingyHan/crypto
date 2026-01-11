package com.crypto.common.exception;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 加密货币系统自定义异常
 *
 * @author Qingyang Han
 * @since 1.0.0
 */

@Setter
@Getter
public class CryptoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private String errorCode;

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
