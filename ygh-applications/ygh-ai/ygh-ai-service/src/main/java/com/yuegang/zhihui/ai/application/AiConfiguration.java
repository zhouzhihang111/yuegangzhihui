package com.yuegang.zhihui.ai.application;
import com.fasterxml.jackson.databind.ObjectMapper;import com.yuegang.zhihui.ai.domain.*;import com.yuegang.zhihui.ai.infrastructure.*;import com.yuegang.zhihui.ai.security.AiUserResolver;import io.micrometer.core.instrument.MeterRegistry;import java.util.*;import javax.sql.DataSource;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)class AiConfiguration{
 @Bean AiGovernanceService aiGovernanceService(DataSource dataSource){return new AiGovernanceService(dataSource);}
 @Bean AiSafetyPolicy aiSafetyPolicy(DataSource dataSource){return new AiSafetyPolicy(dataSource);}
 @Bean SystemAiProviderConfigClient systemAiProviderConfigClient(@Value("${ygh.ai.system-base-url}")String base,@Value("${ygh.internal-request.hmac-base64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new SystemAiProviderConfigClient(base,key);}finally{Arrays.fill(key,(byte)0);}}
 @Bean ModelGateway modelGateway(SystemAiProviderConfigClient configs,AiGovernanceService governance,MeterRegistry metrics){return new MeasuredModelGateway(new GovernedModelGateway(new DynamicModelGateway(configs),governance),metrics);}
 @Bean RetrievalGateway retrievalGateway(@Value("${ygh.ai.search-base-url}")String base,@Value("${ygh.internal-request.hmac-base64}")String encoded,AiSafetyPolicy policy,MeterRegistry metrics){byte[]key=Base64.getDecoder().decode(encoded);try{return new MeasuredRetrievalGateway(new GovernedRetrievalGateway(new HttpRetrievalGateway(base,key),policy),metrics);}finally{Arrays.fill(key,(byte)0);}}
 @Bean CommerceToolGateway commerceToolGateway(@Value("${ygh.ai.product-base-url}")String product,@Value("${ygh.ai.inventory-base-url}")String inventory,@Value("${ygh.ai.order-base-url}")String order,@Value("${ygh.internal-request.hmac-base64}")String encoded,ObjectMapper json){byte[]key=Base64.getDecoder().decode(encoded);try{return new CommerceToolGateway(product,inventory,order,key,json);}finally{Arrays.fill(key,(byte)0);}}
 @Bean ChatService chatService(RetrievalGateway retrieval,ModelGateway model,CommerceToolGateway tools,DataSource dataSource,ObjectMapper json){return new ChatService(retrieval,model,tools,dataSource,json);}
 @Bean AiEvaluationService aiEvaluationService(DataSource dataSource,ChatService chat){return new AiEvaluationService(dataSource,chat);}
 @Bean ConversationService conversationService(DataSource dataSource){return new ConversationService(dataSource);}
 @Bean AiUserResolver aiUserResolver(@Value("${ygh.internal-request.hmac-base64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new AiUserResolver(key);}finally{Arrays.fill(key,(byte)0);}}
}
