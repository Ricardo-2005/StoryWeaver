package com.storyweaver.llm.adapter;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.storyweaver.llm.application.EmbeddingGateway;
import com.storyweaver.llm.config.EmbeddingProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class LocalOnnxEmbeddingGateway implements EmbeddingGateway {
    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingGateway.class);

    private final EmbeddingProperties properties;
    private final ResourceLoader resourceLoader;
    private volatile EmbeddingRuntime runtime;
    private volatile String initializationFailure;

    public LocalOnnxEmbeddingGateway(EmbeddingProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (!properties.enabled()) {
            return EmbeddingResult.unavailable(properties.modelName(), "embedding_disabled");
        }
        try {
            EmbeddingRuntime current = runtime();
            Encoding encoding;
            synchronized (current.tokenizer()) {
                encoding = current.tokenizer().encode(text);
            }
            float[] vector = infer(current, encoding);
            if (vector.length != properties.dimensions()) {
                return EmbeddingResult.unavailable(properties.modelName(), "embedding_dimension_mismatch");
            }
            normalize(vector);
            return EmbeddingResult.available(vector, properties.modelName());
        } catch (RuntimeException | IOException | OrtException | LinkageError exception) {
            initializationFailure = "embedding_model_unavailable";
            log.warn(
                    "Local embedding unavailable; semantic retrieval is degraded ({})",
                    exception.getClass().getSimpleName());
            log.debug("Local embedding failure", exception);
            return EmbeddingResult.unavailable(properties.modelName(), initializationFailure);
        }
    }

    private float[] infer(EmbeddingRuntime current, Encoding encoding) throws OrtException {
        long[][] inputIds = {encoding.getIds()};
        long[][] attentionMask = {encoding.getAttentionMask()};
        long[][] typeIds = {encoding.getTypeIds()};
        try (OnnxTensor ids = OnnxTensor.createTensor(current.environment(), inputIds);
                OnnxTensor mask = OnnxTensor.createTensor(current.environment(), attentionMask);
                OnnxTensor types = OnnxTensor.createTensor(current.environment(), typeIds)) {
            Map<String, OnnxTensor> inputs = Map.of("input_ids", ids, "attention_mask", mask, "token_type_ids", types);
            try (OrtSession.Result result = current.session().run(inputs)) {
                OnnxTensor lastHiddenState = (OnnxTensor) result.get(0);
                FloatBuffer values = lastHiddenState.getFloatBuffer();
                if (values.remaining() < properties.dimensions()) {
                    return new float[0];
                }
                float[] clsEmbedding = new float[properties.dimensions()];
                values.get(clsEmbedding);
                return clsEmbedding;
            }
        }
    }

    private EmbeddingRuntime runtime() throws IOException, OrtException {
        EmbeddingRuntime current = runtime;
        if (current != null) return current;
        if (initializationFailure != null) throw new IllegalStateException(initializationFailure);
        synchronized (this) {
            if (runtime == null) {
                Path tokenizerPath = resourceLoader
                        .getResource(properties.tokenizerUri())
                        .getFile()
                        .toPath();
                Path modelPath = resourceLoader
                        .getResource(properties.modelUri())
                        .getFile()
                        .toPath();
                HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
                        tokenizerPath, Map.of("truncation", "true", "maxLength", "512"));
                try {
                    OrtEnvironment environment = OrtEnvironment.getEnvironment();
                    runtime = new EmbeddingRuntime(
                            environment, environment.createSession(modelPath.toString()), tokenizer);
                } catch (RuntimeException | OrtException | LinkageError exception) {
                    tokenizer.close();
                    throw exception;
                }
            }
            return runtime;
        }
    }

    private void normalize(float[] vector) {
        double squaredNorm = 0;
        for (float value : vector) squaredNorm += value * value;
        if (squaredNorm == 0) return;
        double norm = Math.sqrt(squaredNorm);
        for (int index = 0; index < vector.length; index++) vector[index] /= (float) norm;
    }

    @PreDestroy
    void close() throws OrtException {
        EmbeddingRuntime current = runtime;
        if (current != null) {
            current.session().close();
            current.tokenizer().close();
        }
    }

    private record EmbeddingRuntime(OrtEnvironment environment, OrtSession session, HuggingFaceTokenizer tokenizer) {}
}
