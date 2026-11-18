package com.aimall.ai.model;

public record KnowledgeSource(String docName, String modelNo, Integer chunkIndex, Double score, String text) {
}
