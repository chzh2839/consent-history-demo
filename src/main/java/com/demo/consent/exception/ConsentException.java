package com.demo.consent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ConsentException extends RuntimeException {
    public ConsentException(String message) {
        super(message);
    }
}
