package com.dataagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * A lightweight adaptive "world model" porting RSI-Anything's predictive
 * foundation into DataAgent's RSI harness. It learns the mapping from a harness
 * genome to its evaluation outcome (quality, cost, latency) using normalized
 * least-mean-squares (the delta rule), predicts candidate outcomes before
 * evaluation so the harness can rank children without spending the full
 * evaluation budget, and reports prediction reliability so the harness can
 * decide when to conditionally re-adapt.
 *
 * <p>The model is deliberately simple: eight normalized features (a bias term,
 * topK/20, maxRows/500, parallelism/8 and four boolean flags) map to three
 * scalar readouts. Cost and latency are learned in a normalized space (scaled
 * by 1/1000) to keep the gradient steps stable.
 */
public final class RsiWorldModel {

    public static final int FEATURE_DIM = 8;
    private static final double COST_SCALE = 1000.0;
    private static final double LATENCY_SCALE = 1000.0;

    private final double[] wQuality = new double[FEATURE_DIM];
    private final double[] wCost = new double[FEATURE_DIM];
    private final double[] wLatency = new double[FEATURE_DIM];
    private int samplesSeen;
    private double learningRate;

    public RsiWorldModel() {
        this(0.5);
    }

    public RsiWorldModel(double learningRate) {
        this.learningRate = learningRate;
    }

    /** One observed (features → outcome) pair used to fit and assess the model. */
    public record Sample(double[] features, double quality, double cost, double latency) {
    }

    /** A point prediction of the three outcome readouts. */
    public record Prediction(double quality, double cost, double latency) {
    }

    public int samplesSeen() {
        return samplesSeen;
    }

    public Prediction predict(Map<String, Object> genome) {
        double[] features = features(genome);
        return new Prediction(dot(wQuality, features),
                dot(wCost, features) * COST_SCALE,
                dot(wLatency, features) * LATENCY_SCALE);
    }

    public void fit(List<Sample> samples) {
        for (Sample sample : samples) {
            adapt(sample);
        }
    }

    public void adapt(Sample sample) {
        double[] features = sample.features();
        step(wQuality, features, sample.quality());
        step(wCost, features, sample.cost() / COST_SCALE);
        step(wLatency, features, sample.latency() / LATENCY_SCALE);
        samplesSeen++;
    }

    /** Mean absolute prediction error on the quality readout (0..1 scale). */
    public double qualityMae(List<Sample> samples) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0;
        for (Sample sample : samples) {
            sum += Math.abs(dot(wQuality, sample.features()) - sample.quality());
        }
        return sum / samples.size();
    }

    /**
     * Classifies the model against the RSI-Anything reliability protocol:
     * too little history is {@code insufficient} (not evidence of reliability),
     * otherwise {@code reliable}/{@code unreliable} by a quality-MAE threshold.
     */
    public String reliability(double mae, int minSamples, double maeThreshold) {
        if (samplesSeen < minSamples) {
            return "insufficient";
        }
        return mae <= maeThreshold ? "reliable" : "unreliable";
    }

    public static double[] features(Map<String, Object> genome) {
        double topK = num(genome, "topK", 5) / 20.0;
        double maxRows = num(genome, "maxRows", 100) / 500.0;
        double parallelism = num(genome, "parallelism", 1) / 8.0;
        return new double[]{
                1.0,
                topK,
                bool(genome.get("includeVerified")),
                bool(genome.get("includeMemory")),
                bool(genome.get("planFirst")),
                maxRows,
                parallelism,
                bool(genome.get("selfCheck"))
        };
    }

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "wQuality", wQuality,
                    "wCost", wCost,
                    "wLatency", wLatency,
                    "samplesSeen", samplesSeen,
                    "learningRate", learningRate));
        } catch (Exception error) {
            return "{}";
        }
    }

    public static RsiWorldModel fromJson(String json, ObjectMapper objectMapper) {
        RsiWorldModel model = new RsiWorldModel();
        if (json == null || json.isBlank()) {
            return model;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            copyInto(model.wQuality, map.get("wQuality"));
            copyInto(model.wCost, map.get("wCost"));
            copyInto(model.wLatency, map.get("wLatency"));
            model.samplesSeen = number(map, "samplesSeen", 0);
            model.learningRate = numberDouble(map, "learningRate", 0.5);
            return model;
        } catch (Exception error) {
            return model;
        }
    }

    private void step(double[] weights, double[] features, double target) {
        double error = target - dot(weights, features);
        for (int i = 0; i < weights.length; i++) {
            weights[i] += learningRate * error * features[i];
        }
    }

    private static double dot(double[] left, double[] right) {
        double sum = 0;
        for (int i = 0; i < left.length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    private static void copyInto(double[] target, Object value) {
        if (value instanceof List<?> list) {
            for (int i = 0; i < Math.min(target.length, list.size()); i++) {
                if (list.get(i) instanceof Number number) {
                    target[i] = number.doubleValue();
                }
            }
        }
    }

    private static double num(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int number(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double numberDouble(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static double bool(Object value) {
        return Boolean.TRUE.equals(value) ? 1.0 : 0.0;
    }
}
