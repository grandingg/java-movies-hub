package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie add(String title, int year) {
        Movie movie = new Movie(nextId++, title, year);
        movies.put(movie.getId(), movie);
        return movie;
    }

    public Movie getById(int id) {
        return movies.get(id);
    }

    public void delete(int id) {
        movies.remove(id);
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }
}