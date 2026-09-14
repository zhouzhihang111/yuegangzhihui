package com.yuegang.zhihui.knowledge.application;
import static org.mockito.Mockito.*;import com.yuegang.zhihui.common.mq.JdbcOutboxDispatcher;import org.junit.jupiter.api.Test;
class KnowledgeOutboxConfigurationTest{@Test void delegates(){var d=mock(JdbcOutboxDispatcher.class);new KnowledgeOutboxConfiguration.KnowledgeOutboxJob(d).dispatch();verify(d).dispatch();}}
