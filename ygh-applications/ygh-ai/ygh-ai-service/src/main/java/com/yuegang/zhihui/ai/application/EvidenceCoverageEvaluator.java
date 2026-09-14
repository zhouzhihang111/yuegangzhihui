package com.yuegang.zhihui.ai.application;

import com.yuegang.zhihui.ai.api.ChatResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class EvidenceCoverageEvaluator {
    private static final double PASSING_COVERAGE = 0.60;

    Evaluation evaluate(ChatResponse response, String expectedEvidence, String forbiddenAnswer,
            boolean expectedRefusal) {
        if (expectedRefusal) {
            return response.refused() && "INSUFFICIENT_EVIDENCE".equals(response.refusalReason())
                    ? new Evaluation(true, 100, null)
                    : new Evaluation(false, 0, "REFUSAL_EXPECTED");
        }
        if (response.refused()) return new Evaluation(false, 0, "REFUSED_OR_INSUFFICIENT_EVIDENCE");
        if (response.citations().isEmpty()) return new Evaluation(false, 0, "CITATION_MISSING");
        String searchable = (response.answer() + " " + response.citations().stream()
                .map(citation -> citation.title() + " " + citation.excerpt())
                .reduce("", (left, right) -> left + " " + right)).toLowerCase(Locale.ROOT);
        if (forbiddenAnswer != null && !forbiddenAnswer.isBlank()
                && searchable.contains(forbiddenAnswer.strip().toLowerCase(Locale.ROOT))) {
            return new Evaluation(false, 0, "FORBIDDEN_ANSWER_MATCHED");
        }
        List<String> points = Arrays.stream(expectedEvidence.split("[、,，;；]"))
                .map(String::strip)
                .filter(point -> !point.isBlank())
                .toList();
        if (points.isEmpty()) return new Evaluation(false, 0, "EXPECTED_EVIDENCE_INVALID");
        long covered = points.stream()
                .map(point -> point.toLowerCase(Locale.ROOT))
                .filter(searchable::contains)
                .count();
        double score = covered * 100.0 / points.size();
        return score >= PASSING_COVERAGE
                ? new Evaluation(true, score, null)
                : new Evaluation(false, score, "EXPECTED_EVIDENCE_MISSING");
    }

    record Evaluation(boolean passed, double score, String reason) { }
}
