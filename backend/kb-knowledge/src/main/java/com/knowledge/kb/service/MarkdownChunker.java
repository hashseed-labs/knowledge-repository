package com.knowledge.kb.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarkdownChunker {

    public List<String> chunk(String markdown, int maxChars, int overlapChars) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String normalized = markdown.replace("\r\n", "\n").trim();
        List<String> sections = splitByHeadings(normalized);
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            if (section.length() <= maxChars) {
                chunks.add(section.trim());
            } else {
                chunks.addAll(splitByLength(section, maxChars, overlapChars));
            }
        }
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    private List<String> splitByHeadings(String text) {
        String[] lines = text.split("\n", -1);
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.matches("^#{1,6}\\s+.+") && current.length() > 0) {
                sections.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        if (sections.isEmpty()) {
            sections.add(text);
        }
        return sections;
    }

    private List<String> splitByLength(String text, int maxChars, int overlapChars) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int breakAt = findBreak(text, start, end);
                if (breakAt > start) {
                    end = breakAt;
                }
            }
            result.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
        return result;
    }

    private int findBreak(String text, int start, int end) {
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > start + (end - start) / 3) {
            return paragraph;
        }
        int newline = text.lastIndexOf('\n', end);
        if (newline > start + (end - start) / 3) {
            return newline;
        }
        int space = text.lastIndexOf(' ', end);
        if (space > start + (end - start) / 3) {
            return space;
        }
        return end;
    }
}
