package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

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
        sendJson(ex, 200, gson.toJson(movies));
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

        List<String> errors = validateMovie(title, yearStr);

        if (!errors.isEmpty()) {
            sendJson(ex, 422, buildValidationError(errors));
            return;
        }

        int year = Integer.parseInt(yearStr);

        Movie movie = store.add(title, year);

        sendJson(ex, 201, gson.toJson(movie));
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

        sendJson(ex, 200, gson.toJson(movie));
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
                .toList();

        sendJson(ex, 200, gson.toJson(movies));
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

    private List<String> validateMovie(String title, String yearStr) {

        List<String> errors = new ArrayList<>();

        if (title == null || title.isBlank()) {
            errors.add("название не должно быть пустым");
        }

        if (title != null && title.length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int year;

        try {
            year = Integer.parseInt(yearStr);
        } catch (Exception e) {
            errors.add("год должен быть числом");
            return errors;
        }

        int currentYear = Year.now().getValue();

        if (year < 1888 || year > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }

        return errors;
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
        return gson.toJson(new ru.practicum.moviehub.api.ErrorResponse("Ошибка валидации", details));
    }
}