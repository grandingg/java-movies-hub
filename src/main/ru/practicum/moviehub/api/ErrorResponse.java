package ru.practicum.moviehub.api;

import java.util.List;

public class ErrorResponse {

    private final String error;
    private final List<String> details;

    public ErrorResponse(String error) {
        this.error = error;
        this.details = null;
    }

    public ErrorResponse(String error, List<String> details) {
        this.error = error;
        this.details = details;
    }

    public String toJson() {
        if (details == null || details.isEmpty()) {
            return String.format("{\"error\":\"%s\"}", error);
        }

        String joined = details.stream()
                .map(d -> "\"" + d + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return String.format(
                "{\"error\":\"%s\",\"details\":[%s]}",
                error,
                joined
        );
    }
}