package com.knowledge.kb.integration;

import java.util.function.Consumer;

public interface LlmClient {

    void generateStream(String prompt, double temperature, Consumer<String> onToken);

    boolean ping();

    String provider();
}
