package com.fingermemory.server.adventure;

import com.fingermemory.server.common.ApiException;
import org.springframework.http.HttpStatus;

public class AdventureException extends ApiException {
    public AdventureException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
