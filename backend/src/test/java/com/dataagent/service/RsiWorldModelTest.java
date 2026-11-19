package com.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsiWorldModelTest {

    @Test
    void featuresAreNormalizedWithBias() {
        Map<String, Object> genome = new HashMap<>();
        genome.put("topK", 10);
        genome.put("includeVerified", true);
        genome.put("includeMemory", false);
        genome.put("planFirst", true);
        genome.put("maxRows", 250);
        genome.put("parallelism", 2);
        genome.put("selfCheck", true);

        double[] features = RsiWorldModel.features(genome);
        assertEquals(8, features.length);
        assertEquals(1.0, features[0], 1e-9);          // bias
        assertEquals(0.5, features[1], 1e-9);          // topK/20
        assertEquals(1.0, features[2], 1e-9);          // includeVerified
        assertEquals(0.0, features[3], 1e-9);          // includeMemory
        assertEquals(1.0, features[4], 1e-9);          // planFirst
        assertEquals(0.5, features[5], 1e-9);          // maxRows/500
        assertEquals(0.25, features[6], 1e-9);         // parallelism/8
        assertEquals(1.0, features[7], 1e-9);          // selfCheck
    }

    @Test
    void freshModelPredictsZero() {
        Map<String, Object> genome = new HashMap<>();
        genome.put("topK", 10);
        RsiWorldModel.Prediction prediction = new RsiWorldModel().predict(genome);
        assertEquals(0.0, prediction.quality(), 1e-9);
        assertEquals(0.0, prediction.cost(), 1e-9);
        assertEquals(0.0, prediction.latency(), 1e-9);
    }

    @Test
    void fittingReducesPredictionErrorOnLearnedQuality() {
        RsiWorldModel model = new RsiWorldModel(0.1);
        Map<String, Object> genome = new HashMap<>();
        genome.put("topK", 10);
        genome.put("includeVerified", true);
        genome.put("includeMemory", false);
        genome.put("planFirst", true);
        genome.put("maxRows", 200);
        genome.put("parallelism", 2);
        genome.put("selfCheck", true);
        double[] features = RsiWorldModel.features(genome);
        double targetQuality = 0.8;
        List<RsiWorldModel.Sample> samples = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            samples.add(new RsiWorldModel.Sample(features, targetQuality, 100, 50));
        }
        model.fit(samples);
        double predicted = model.predict(genome).quality();
        assertTrue(Math.abs(predicted - targetQuality) < 0.05,
                "expected quality near " + targetQuality + " but got " + predicted);
    }

    @Test
    void reliabilityTransitionsWithSamplesAndError() {
        RsiWorldModel model = new RsiWorldModel();
        assertEquals("insufficient", model.reliability(0.01, 10, 0.05));
        model.adapt(new RsiWorldModel.Sample(RsiWorldModel.features(Map.of("topK", 5)), 0.7, 100, 50));
        model.adapt(new RsiWorldModel.Sample(RsiWorldModel.features(Map.of("topK", 5)), 0.7, 100, 50));
        assertEquals("insufficient", model.reliability(0.01, 3, 0.05)); // only 2 samples
        assertEquals("reliable", model.reliability(0.01, 2, 0.05));
        assertEquals("unreliable", model.reliability(0.5, 2, 0.05));
    }

    @Test
    void jsonRoundTripPreservesPredictions() {
        RsiWorldModel model = new RsiWorldModel(0.2);
        Map<String, Object> genome = new HashMap<>();
        genome.put("topK", 12);
        genome.put("maxRows", 100);
        genome.put("parallelism", 1);
        model.fit(List.of(new RsiWorldModel.Sample(RsiWorldModel.features(genome), 0.9, 120, 80)));
        ObjectMapper objectMapper = new ObjectMapper();

        RsiWorldModel restored = RsiWorldModel.fromJson(model.toJson(objectMapper), objectMapper);
        assertEquals(model.predict(genome).quality(), restored.predict(genome).quality(), 1e-9);
        assertEquals(model.samplesSeen(), restored.samplesSeen());
    }
}
