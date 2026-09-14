package com.yuegang.zhihui.knowledge.application;
import com.fasterxml.jackson.databind.ObjectMapper;import com.yuegang.zhihui.knowledge.security.*;import java.util.*;import javax.sql.DataSource;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;
@Configuration(proxyBeanMethods=false)class KnowledgeConfiguration{
 @Bean KnowledgeParseDispatcher knowledgeParseDispatcher(DataSource dataSource,@Value("${ygh.knowledge.storage-root}")String root){return new KnowledgeParseDispatcher(dataSource,root);}
 @Bean KnowledgeDocumentService knowledgeDocumentService(DataSource dataSource,@Value("${ygh.knowledge.storage-root}")String root,KnowledgeParseDispatcher parser){return new KnowledgeDocumentService(dataSource,root,parser);}
 @Bean KnowledgeLifecycleService knowledgeLifecycleService(DataSource dataSource){return new KnowledgeLifecycleService(dataSource);}
 @Bean KnowledgeMetadataService knowledgeMetadataService(DataSource dataSource,ObjectMapper json){return new KnowledgeMetadataService(dataSource,json);}
 @Bean KnowledgeAccessGuard knowledgeAccessGuard(DataSource dataSource){return new KnowledgeAccessGuard(dataSource);}
 @Bean KnowledgeIndexJobService knowledgeIndexJobService(DataSource dataSource){return new KnowledgeIndexJobService(dataSource);}
 @Bean KnowledgeProcessingJobService knowledgeProcessingJobService(DataSource dataSource,KnowledgeParseDispatcher dispatcher){return new KnowledgeProcessingJobService(dataSource,dispatcher);}
 @Bean KnowledgeExpiryJob knowledgeExpiryJob(JdbcTemplate jdbc,PlatformTransactionManager transactions){return new KnowledgeExpiryJob(jdbc,transactions);}
 @Bean KnowledgeIndexDispatcher knowledgeIndexDispatcher(JdbcTemplate jdbc,@Value("${ygh.knowledge.search-base-url}")String base,@Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new KnowledgeIndexDispatcher(jdbc,base,key);}finally{Arrays.fill(key,(byte)0);}}
 @Bean KnowledgeSearchGateway knowledgeSearchGateway(@Value("${ygh.knowledge.search-base-url}")String base,@Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new KnowledgeSearchGateway(base,key);}finally{Arrays.fill(key,(byte)0);}}
 @Bean KnowledgeUserResolver knowledgeUserResolver(@Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new KnowledgeUserResolver(key);}finally{Arrays.fill(key,(byte)0);}}
}
