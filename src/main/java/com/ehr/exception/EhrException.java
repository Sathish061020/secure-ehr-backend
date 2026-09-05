package com.ehr.exception;

import lombok.Getter;

@Getter
public class EhrException extends RuntimeException {
    private final int statusCode;

    public EhrException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
