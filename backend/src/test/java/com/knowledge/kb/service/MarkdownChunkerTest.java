package com.knowledge.kb.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    void shouldSplitByHeading() {
        String md = "# A\nhello\n\n## B\n" + "x".repeat(20);
        List<String> chunks = chunker.chunk(md, 600, 80);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(c -> c.contains("# A")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("## B")));
    }
}
