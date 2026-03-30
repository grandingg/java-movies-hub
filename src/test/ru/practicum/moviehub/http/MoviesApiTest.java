package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }
 //всего 15 тестов
    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> response = client
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());

        String contentType = response.headers()
                .firstValue("Content-Type").orElse("");

        assertEquals("application/json; charset=UTF-8", contentType);

        List<Movie> movies = gson.fromJson(response.body(),
                new ListOfMoviesTypeToken().getType());

        assertTrue(movies.isEmpty());
    }

    @Test
    void postMovie_whenValid_returnsCreatedMovie() throws Exception {

        String jsonBody = """
                {
                  "title": "Inception",
                  "year": 2010
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, response.statusCode());

        String contentType =
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("");

        assertEquals("application/json; charset=UTF-8", contentType);

        Movie movie = gson.fromJson(response.body(), Movie.class);

        assertEquals(1, movie.getId());
        assertEquals("Inception", movie.getTitle());
        assertEquals(2010, movie.getYear());
    }

    @Test
    void postMovie_whenTitleEmpty_returns422() throws Exception {

        String jsonBody = """
                {
                  "title": "",
                  "year": 2010
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode());

        String body = response.body();

        assertTrue(body.contains("Ошибка валидации"));
        assertTrue(body.contains("название не должно быть пустым"));
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {

        String jsonBody = """
                {
                  "title": "Matrix",
                  "year": 1999
                }
                """;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.send(postRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());

        Movie movie = gson.fromJson(response.body(), Movie.class);

        assertEquals(1, movie.getId());
        assertEquals("Matrix", movie.getTitle());
        assertEquals(1999, movie.getYear());
    }

    @Test
    void getMovieById_whenNotExists_returns404() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode());

        String body = response.body();
        assertTrue(body.contains("Фильм не найден"));
    }

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {

        String jsonBody = """
                {
                  "title": "Avatar",
                  "year": 2009
                }
                """;

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.send(postRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse =
                client.send(deleteRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, deleteResponse.statusCode());

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> getResponse =
                client.send(getRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResponse.statusCode());
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Некорректный ID"));
    }

    @Test
    void getMovies_whenFilterByYear_returnsCorrectMovies() throws Exception {

        String movie1 = """
                { "title": "Movie1", "year": 2000 }
                """;

        String movie2 = """
                { "title": "Movie2", "year": 2010 }
                """;

        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(movie1))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/movies"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(movie2))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());

        List<Movie> movies =
                gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(1, movies.size());
        assertEquals("Movie2", movies.getFirst().getTitle());
    }

    @Test
    void postMovie_whenTitleTooLong_returns422() throws Exception {

        String longTitle = "A".repeat(101); //Ничего другого не придумала ахахахха

        String jsonBody = """
                {
                  "title": "%s",
                  "year": 2000
                }
                """.formatted(longTitle);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("название не должно превышать 100 символов"));
    }

    @Test
    void postMovie_whenYearTooSmall_returns422() throws Exception {

        String jsonBody = """
                {
                  "title": "Old Movie",
                  "year": 1800
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("год должен быть между"));
    }

    @Test
    void postMovie_whenWrongContentType_returns415() throws Exception {

        String jsonBody = """
                {
                  "title": "Test",
                  "year": 2000
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode());
    }

    @Test
    void postMovie_whenInvalidJson_returns422() throws Exception {

        String jsonBody = "{ invalid json }";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, response.statusCode());
    }

    @Test
    void getMovies_whenYearNotNumber_returns400() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, response.statusCode());
    }

    @Test
    void deleteMovie_whenNotExists_returns404() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, response.statusCode());
    }
}