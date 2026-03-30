package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {

        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            handleGet(ex);
        } else if (method.equalsIgnoreCase("POST")) {
            handlePost(ex);
        } else if (method.equalsIgnoreCase("DELETE")) {
            handleDelete(ex);
        } else {
            sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (path.startsWith("/movies/")) {
            handleGetId(ex, path);
            return;
        }

        if (query != null) {
            if (!query.startsWith("year=")) {
                sendJson(ex, 400, "{\"error\":\"Некорректный параметр запроса\"}");
                return;
            }
            handleGetByYear(ex, query);
            return;
        }

        List<Movie> movies = store.getAll();

        String json = movies.stream()
                .map(m -> String.format(
                        "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                        m.getId(),
                        m.getTitle(),
                        m.getYear()
                ))
                .collect(Collectors.joining(",", "[", "]"));

        sendJson(ex, 200, json);
    }

    private void handlePost(HttpExchange ex) throws IOException {

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendJson(ex, 415, "{\"error\":\"Unsupported Media Type\"}");
            return;
        }

        String body = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        String title = extractValue(body, "title");
        String yearStr = extractValue(body, "year");

        if (title == null || yearStr == null) {
            sendJson(ex, 422, "{\"error\":\"Некорректный JSON\"}");
            return;
        }

        title = title.replace("\"", "");

        List<String> errors = new ArrayList<>();

        if (title.isBlank()) {
            errors.add("название не должно быть пустым");
        }

        if (title.length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int year = 0;

        try {
            year = Integer.parseInt(yearStr);
        } catch (Exception e) {
            errors.add("год должен быть числом");
        }

        int currentYear = Year.now().getValue();

        if (year < 1888 || year > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        if (!errors.isEmpty()) {
            String errorJson = buildValidationError(errors);
            sendJson(ex, 422, errorJson);
            return;
        }

        Movie movie = store.add(title, year);

        String json = String.format(
                "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                movie.getId(),
                movie.getTitle(),
                movie.getYear()
        );

        sendJson(ex, 201, json);
    }

    private void handleGetId(HttpExchange ex, String path) throws IOException {

        String idPart = path.substring("/movies/".length());

        int id;

        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный ID\"}");
            return;
        }

        Movie movie = store.getById(id);

        if (movie == null) {
            sendJson(ex, 404, "{\"error\":\"Фильм не найден\"}");
            return;
        }

        String json = String.format(
                "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                movie.getId(),
                movie.getTitle(),
                movie.getYear()
        );

        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {

        String yearStr = query.substring("year=".length());

        int year;

        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный параметр запроса — 'year'\"}");
            return;
        }

        List<Movie> movies = store.getAll().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());

        String json = movies.stream()
                .map(m -> String.format(
                        "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                        m.getId(),
                        m.getTitle(),
                        m.getYear()
                ))
                .collect(Collectors.joining(",", "[", "]"));

        sendJson(ex, 200, json);
    }

    private void handleDelete(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();

        if (!path.startsWith("/movies/")) {
            sendJson(ex, 404, "{\"error\":\"Фильм не найден\"}");
            return;
        }

        String idPart = path.substring("/movies/".length());

        int id;

        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный ID\"}");
            return;
        }

        Movie movie = store.getById(id);

        if (movie == null) {
            sendJson(ex, 404, "{\"error\":\"Фильм не найден\"}");
            return;
        }

        store.delete(id);

        sendNoContent(ex);
    }

    private String extractValue(String json, String field) {
        try {
            String pattern = "\"" + field + "\":";
            int start = json.indexOf(pattern) + pattern.length();

            if (json.charAt(start) == '"') {
                start++;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            } else {
                int end = json.indexOf(",", start);
                if (end == -1) {
                    end = json.indexOf("}", start);
                }
                return json.substring(start, end).trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String buildValidationError(List<String> details) {
        String joined = details.stream()
                .map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(","));

        return String.format(
                "{\"error\":\"Ошибка валидации\",\"details\":[%s]}",
                joined
        );
    }
}