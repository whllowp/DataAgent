package com.dataagent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Local, dependency-free embedding fallback: hashed word/character n-grams
 * with cosine similarity. It is deterministic and works for Chinese text
 * without an external model. A remote embedding provider can replace this
 * implementation without changing callers.
 */
@Service
public class EmbeddingService {

    private static final int DIMENSION = 256;
    private static final int MAX_RECALL = 20;
    private static final double MIN_SCORE = 0.06;

    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        List<String> features = new ArrayList<>();
        for (String token : normalized.replaceAll("[\\p{Punct}，。；：！？、]+", " ").split("\\s+")) {
            if (!token.isBlank()) {
                features.add("w:" + token);
                if (token.length() > 2) {
                    for (int i = 0; i < token.length() - 1; i++) {
                        features.add("b:" + token.substring(i, i + 2));
                    }
                }
            }
        }
        for (String feature : features) {
            int hash = feature.hashCode();
            int index = Math.floorMod(hash, DIMENSION);
            vector[index] += (hash & 1) == 0 ? 1f : -1f;
        }
        normalize(vector);
        return vector;
    }

    public double similarity(float[] left, float[] right) {
        if (left == null || right == null) {
            return 0;
        }
        double dot = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }

    public <T> List<T> rank(String query, List<T> items, Function<T, String> text, int limit) {
        if (query == null || query.isBlank()) {
            return items.stream().limit(limit).toList();
        }
        float[] queryVector = embed(query);
        return items.stream()
                .map(item -> new Scored<>(item, similarity(queryVector, embed(text.apply(item)))))
                .filter(scored -> scored.score >= MIN_SCORE)
                .sorted(Comparator.comparingDouble((Scored<T> scored) -> scored.score).reversed())
                .limit(Math.min(limit, MAX_RECALL))
                .map(Scored::item)
                .toList();
    }

    private static void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm <= 0) {
            return;
        }
        double length = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / length);
        }
    }

    private record Scored<T>(T item, double score) {
    }
}
