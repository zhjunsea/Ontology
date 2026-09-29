package com.ocean.ontopobdahandler;

public class WriteResult {
    private final boolean accepted;
    private final String message;

    private WriteResult(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    public static WriteResult accepted(String message) { return new WriteResult(true, message); }
    public static WriteResult rejected(String message) { return new WriteResult(false, message); }

    public boolean isAccepted() { return accepted; }
    public String getMessage() { return message; }
}