package com.storyweaver.importing.book.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TxtImportModels {
    private TxtImportModels() {}

    public enum EncodingChoice {
        AUTO,
        UTF_8,
        GB18030,
        GBK
    }

    public record StoredSource(
            UUID id,
            String originalFilename,
            String storageKey,
            long sizeBytes,
            String sha256,
            String detectedEncoding,
            String selectedEncoding,
            boolean encodingConfident,
            Instant expiresAt) {}

    public record ChapterCandidate(
            UUID id,
            int sequenceNo,
            String title,
            long startOffset,
            long endOffset,
            long characterCount,
            int paragraphCount,
            boolean included) {}

    public record ImportView(
            UUID id,
            UUID sourceId,
            UUID projectId,
            String status,
            String analysisStatus,
            String filename,
            long sizeBytes,
            String sha256,
            String detectedEncoding,
            String selectedEncoding,
            boolean encodingConfident,
            long totalCharacters,
            int totalChapters,
            int processedChapters,
            int headingCount,
            int analysisProcessedChunks,
            String parserVersion,
            String errorCode,
            String errorMessage,
            UUID duplicateImportId,
            UUID duplicateProjectId,
            long version,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            List<ChapterCandidate> chapters) {}

    public record ParseResult(
            String normalizedHash, long characterCount, int headingCount, List<ParsedChapter> chapters) {}

    public record ParsedChapter(
            String title, long startOffset, long endOffset, long characterCount, int paragraphCount) {}
}
