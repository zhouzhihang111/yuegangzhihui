package com.yuegang.zhihui.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.ai.api.ChatRequest;
import com.yuegang.zhihui.ai.api.ChatResponse;
import com.yuegang.zhihui.ai.api.CitationView;
import com.yuegang.zhihui.ai.domain.ModelGateway;
import com.yuegang.zhihui.ai.domain.ModelAnswer;
import com.yuegang.zhihui.ai.domain.RetrievalGateway;
import com.yuegang.zhihui.ai.infrastructure.CommerceToolGateway;
import com.yuegang.zhihui.ai.infrastructure.CommerceToolGateway.ToolEvidence;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.search.api.SearchHit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class ChatService {
    private static final String SYSTEM = "你是跨境智汇企业客服。优先依据提供的已审核资料和系统只读工具；启用联网搜索时可用公开网络资料补充知识盲区和时效信息。回答必须区分已审核知识库、只读业务数据、模型通用知识与互联网来源；没有检索到已审核资料时，可以基于模型通用知识给出审慎建议，但必须明确提示该部分未经过企业知识库核验。不得生成SQL、修改订单、调整余额或发布知识。";
    private static final Pattern SKU = Pattern.compile("(?:SKU|商品)[：: #]*(\\d{1,20})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER = Pattern.compile("订单[号ID：: #]*(\\d{1,20})", Pattern.CASE_INSENSITIVE);
    private final RetrievalGateway retrieval;
    private final ModelGateway model;
    private final CommerceToolGateway tools;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public ChatService(RetrievalGateway retrieval, ModelGateway model, CommerceToolGateway tools, DataSource dataSource) {
        this(retrieval, model, tools, dataSource, new ObjectMapper().findAndRegisterModules());
    }

    public ChatService(RetrievalGateway retrieval, ModelGateway model, CommerceToolGateway tools, DataSource dataSource,
                       ObjectMapper json) {
        this.retrieval = retrieval;
        this.model = model;
        this.tools = tools;
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.json = json;
    }

    public ChatResponse chat(long user, ChatRequest request) {
        return chat(user, Set.of("PUBLIC"), request);
    }

    public ChatResponse chat(long user, Set<String> visibilities, ChatRequest request) {
        long retrievalStarted = System.nanoTime();
        List<SearchHit> hits = retrieval.search(request.message(), request.category(), 8, visibilities);
        long retrievalMs = elapsed(retrievalStarted);
        List<ToolEvidence> evidence = new ArrayList<>();
        Matcher sku = SKU.matcher(request.message());
        if (sku.find()) evidence.add(tools.product(sku.group(1)));
        if (request.includeOwnOrders()) {
            Matcher order = ORDER.matcher(request.message());
            if (order.find()) evidence.add(tools.ownOrder(user, order.group(1)));
        }
        Prompt prompt = activePrompt();
        String context = hits.stream().map(hit -> "[" + hit.documentId() + "/" + hit.chunkId() + "] "
                + hit.title() + "\n" + hit.excerpt()).reduce("", (left, right) -> left + "\n" + right);
        String toolContext = evidence.stream().map(value -> "[只读工具 " + value.name() + "] " + value.resultJson())
                .reduce("", (left, right) -> left + "\n" + right);
        String userPrompt = "已审核资料：\n" + context + "\n只读业务数据：\n" + toolContext
                + "\n\n回答要求：优先使用已审核资料和只读业务数据；如果资料不足且联网搜索已启用，请检索多个可信公开来源后回答并保留来源链接；如果资料不足且联网搜索不可用，请基于模型通用知识给出可操作建议，并在开头标注“未命中已审核知识库，以下为通用建议，需人工核验”。\n问题："
                + request.message();
        List<CitationView> knowledgeCitations = hits.stream().map(hit -> new CitationView(
                "KNOWLEDGE", hit.chunkId(), hit.documentId(), hit.title(), hit.excerpt(), null,
                hit.documentVersion(), hit.sourceUpdatedAt())).toList();
        if (!model.available()) {
            String answer = model.answer(SYSTEM, userPrompt);
            Prompt unavailable = new Prompt(prompt.id, prompt.version, model.modelName());
            return save(user, request, answer, knowledgeCitations, evidence, true, "MODEL_NOT_CONFIGURED",
                    new Trace(retrievalMs, 0, unavailable, tokens(SYSTEM) + tokens(userPrompt), tokens(answer), hits));
        }
        long modelStarted = System.nanoTime();
        ModelAnswer generated = model.answerWithSources(SYSTEM, userPrompt);
        String answer = generated.text();
        long modelMs = elapsed(modelStarted);
        List<CitationView> citations = new ArrayList<>(knowledgeCitations);
        generated.sources().forEach(source -> citations.add(new CitationView(
                "WEB", digest(source.url()), source.title(), source.excerpt(), source.url())));
        return save(user, request, answer, citations, evidence, false, null,
                new Trace(retrievalMs, modelMs, prompt, tokens(SYSTEM) + tokens(userPrompt), tokens(answer), hits));
    }

    private ChatResponse save(long user, ChatRequest request, String answer, List<CitationView> citations,
                              List<ToolEvidence> tools, boolean refused, String reason, Trace trace) {
        return transactions.execute(status -> {
            long conversation = request.conversationId() == null || request.conversationId().isBlank()
                    ? next() : Long.parseLong(request.conversationId());
            Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation WHERE id=? AND user_id=?",
                    Integer.class, conversation, user);
            if (request.conversationId() != null && !request.conversationId().isBlank() && owned == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            jdbc.update("INSERT INTO ai_conversation(id,user_id,title) VALUES(?,?,?) ON DUPLICATE KEY UPDATE updated_at=NOW(6)",
                    conversation, user, request.message().substring(0, Math.min(100, request.message().length())));
            jdbc.update("INSERT INTO ai_message(id,conversation_id,role,content) VALUES(?,?,'USER',?)",
                    next(), conversation, request.message());
            long message = next();
            jdbc.update("INSERT INTO ai_message(id,conversation_id,role,content,refused,model_name,prompt_tokens,completion_tokens) VALUES(?,?,'ASSISTANT',?,?,?,?,?)",
                    message, conversation, answer, refused, trace.prompt.modelName, trace.promptTokens, trace.completionTokens);
            int sequence = 0;
            for (CitationView citation : citations) {
                jdbc.update("INSERT INTO ai_citation(message_id,sequence_no,source_type,source_id,document_id,title,excerpt,url,document_version,source_updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        message, sequence++, citation.sourceType(), citation.sourceId(), citation.documentId(),
                        citation.title(), citation.excerpt(), citation.url(), citation.documentVersion(),
                        citation.sourceUpdatedAt() == null ? null : java.sql.Timestamp.valueOf(
                                citation.sourceUpdatedAt().withOffsetSameInstant(java.time.ZoneOffset.UTC)
                                        .toLocalDateTime()));
            }
            for (ToolEvidence tool : tools) {
                jdbc.update("INSERT INTO ai_tool_call(id,message_id,tool_name,arguments_json,result_digest,status) VALUES(?,?,?,?,?,'SUCCEEDED')",
                        next(), message, tool.name(), tool.argumentsJson(), tool.digest());
            }
            jdbc.update("INSERT INTO ai_rag_trace(id,message_id,query_digest,retrieval_evidence_json,prompt_config_id,prompt_version,model_name,retrieval_duration_ms,model_duration_ms,prompt_tokens_estimated,completion_tokens_estimated) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    next(), message, digest(request.message()), write(trace.hits), trace.prompt.id, trace.prompt.version,
                    trace.prompt.modelName, trace.retrievalMs, trace.modelMs, trace.promptTokens, trace.completionTokens);
            return new ChatResponse(Long.toString(conversation), Long.toString(message), answer, citations, refused, reason);
        });
    }

    private Prompt activePrompt() {
        return jdbc.query("SELECT id,version,model_name FROM ai_prompt_config WHERE enabled=TRUE ORDER BY updated_at DESC LIMIT 1",
                result -> result.next() ? new Prompt(result.getLong(1), result.getLong(2), result.getString(3))
                        : new Prompt(null, null, "doubao"));
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
    }
    private static int tokens(String value) { return Math.max(1, value == null ? 0 : value.codePointCount(0, value.length()) / 2); }
    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
    private record Prompt(Long id, Long version, String modelName) {}
    private record Trace(long retrievalMs, long modelMs, Prompt prompt, int promptTokens, int completionTokens,
                         List<SearchHit> hits) {}
}
