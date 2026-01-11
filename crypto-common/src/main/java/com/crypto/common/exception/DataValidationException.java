package com.crypto.common.exception;

import java.io.Serial;

/**
 * 数据验证异常
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class DataValidationException extends CryptoException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataValidationException(String message) {
        super("DATA_VALIDATION_ERROR", message); // 使用父类的构造方法
    }

    public DataValidationException(String message, Throwable cause) {
        super("DATA_VALIDATION_ERROR", message, cause); // 使用父类的构造方法
    }
}
