package com.knowledge.kb.integration;

import java.util.function.Consumer;

public interface LlmClient {

    void generateStream(String prompt, double temperature, Consumer<String> onToken);

    /**
     * 非流式补全，适合出题等需要完整 JSON 的场景。
     */
    String generate(String prompt, double temperature);

    boolean ping();

    String provider();
}
