package com.ocean.ontopobdahandler;

/**
 * 写入结果封装（兼容 Java 8+）
 */
public final class WriteResult {
    private final boolean accepted;
    private final String message;

    private WriteResult(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    public static WriteResult accepted() {
        return new WriteResult(true, "OK");
    }

    public static WriteResult rejected(String msg) {
        return new WriteResult(false, msg);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "WriteResult{accepted=" + accepted + ", message='" + message + "'}";
    }
}