package com.knowledge.kb.integration;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);

    boolean ping();

    String provider();
}
