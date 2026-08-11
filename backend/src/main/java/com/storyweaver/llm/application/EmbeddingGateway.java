package com.storyweaver.llm.application;

public interface EmbeddingGateway {
    EmbeddingResult embed(String text);

    record EmbeddingResult(boolean available, float[] vector, String model, String unavailableReason) {
        public static EmbeddingResult available(float[] vector, String model) {
            return new EmbeddingResult(true, vector.clone(), model, null);
        }

        public static EmbeddingResult unavailable(String model, String reason) {
            return new EmbeddingResult(false, null, model, reason);
        }

        @Override
        public float[] vector() {
            return vector == null ? null : vector.clone();
        }
    }
}
