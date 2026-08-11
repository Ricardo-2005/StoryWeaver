package com.storyweaver.importing.book.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("storyweaver.import.txt")
public record TxtImportProperties(
        DataSize maxFileSize,
        Path storageDirectory,
        Duration sourceRetention,
        int previewMaxCharacters,
        int parserMaxHeadingCharacters,
        int analysisChunkCharacters) {}
