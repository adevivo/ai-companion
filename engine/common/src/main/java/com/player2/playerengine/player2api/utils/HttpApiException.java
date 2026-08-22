package com.player2.playerengine.player2api.utils;

import java.io.IOException;

public class HttpApiException extends IOException {
    private final int statusCode;

    public HttpApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}