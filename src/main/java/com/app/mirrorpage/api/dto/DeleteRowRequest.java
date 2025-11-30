package com.app.mirrorpage.api.dto;

public record DeleteRowRequest(String path, int row, String user) {
}
