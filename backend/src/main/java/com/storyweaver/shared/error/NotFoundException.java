package com.storyweaver.shared.error;

import org.springframework.http.HttpStatus;

public final class NotFoundException extends ApiException {

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
