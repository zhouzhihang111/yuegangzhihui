package com.yuegang.zhihui.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.ai.api.ChatResponse;
import com.yuegang.zhihui.ai.api.CitationView;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceCoverageEvaluatorTest {
    private final EvidenceCoverageEvaluator evaluator = new EvidenceCoverageEvaluator();

    @Test
    void requiresCitationsAndSixtyPercentOfExpectedEvidence() {
        var citation = new CitationView("KNOWLEDGE", "2", "1", "海关发布的有效政策版本",
                "政策生效日期及单次交易限值", null, 3, OffsetDateTime.now());
        var response = new ChatResponse("1", "2", "请按现行规则判断。", List.of(citation), false, null);

        var result = evaluator.evaluate(response, "有效政策版本、发布机构、生效日期、交易限值", "无需核对现行政策", false);

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(75);
    }

    @Test
    void rejectsMissingEvidenceForbiddenAnswersAndRefusals() {
        var citation = new CitationView("KNOWLEDGE", "2", "1", "普通资料", "无关内容", null, 1,
                OffsetDateTime.now());
        assertThat(evaluator.evaluate(new ChatResponse("1", "2", "回答", List.of(citation), false, null),
                "材料、检验检疫、地区", null, false).reason()).isEqualTo("EXPECTED_EVIDENCE_MISSING");
        assertThat(evaluator.evaluate(new ChatResponse("1", "2", "可以跳过检验检疫", List.of(citation), false, null),
                "检验检疫", "可以跳过检验检疫", false).reason()).isEqualTo("FORBIDDEN_ANSWER_MATCHED");
        assertThat(evaluator.evaluate(new ChatResponse("1", "2", "资料不足", List.of(), true, "INSUFFICIENT_EVIDENCE"),
                "政策", null, false).reason()).isEqualTo("REFUSED_OR_INSUFFICIENT_EVIDENCE");
        assertThat(evaluator.evaluate(new ChatResponse("1", "2", "资料不足", List.of(), true, "INSUFFICIENT_EVIDENCE"),
                "应拒答", null, true).passed()).isTrue();
    }
}
