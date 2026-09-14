package com.yuegang.zhihui.ai.infrastructure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.ai.domain.ModelGateway;
import com.yuegang.zhihui.ai.domain.ModelAnswer;
import com.yuegang.zhihui.ai.domain.ModelProviderException;
import com.yuegang.zhihui.ai.domain.ModelSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** 豆包 Responses API 适配器。API Key 仅保存在服务端配置中，不进入日志或前端响应。 */
public final class DoubaoModelGateway implements ModelGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient client;
    private final String modelName;
    private final String responsesUrl;
    private final boolean webSearchEnabled;
    public DoubaoModelGateway(String baseUrl,String apiKey,String modelName){
        this(baseUrl, apiKey, modelName, false);
    }
    public DoubaoModelGateway(String baseUrl,String apiKey,String modelName,boolean webSearchEnabled){
        if(apiKey==null||apiKey.isBlank())throw new IllegalStateException("Doubao key missing");
        this.modelName=modelName;
        this.webSearchEnabled=webSearchEnabled;
        responsesUrl=trimTrailingSlash(baseUrl)+"/responses";
        client=RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey)
                .build();
    }
    @Override public String answer(String system,String user){
        return answerWithSources(system, user).text();
    }
    @Override public ModelAnswer answerWithSources(String system,String user){
        Map<?,?> response;
        try {
            Map<String,Object> request=new LinkedHashMap<>();
            request.put("model",modelName);
            request.put("instructions",system);
            request.put("input",user);
            if(webSearchEnabled)request.put("tools",List.of(Map.of("type","web_search")));
            response=client.post().uri(responsesUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception);
        }
        String text=extractText(response);
        if(text==null||text.isBlank())throw new IllegalStateException("empty model response");
        return new ModelAnswer(text,extractSources(response));
    }
    @Override public String modelName(){return modelName;}
    @Override public boolean supportsWebSearch(){return webSearchEnabled;}

    private static String trimTrailingSlash(String value){
        if(value==null||value.isBlank())throw new IllegalArgumentException("Doubao base URL missing");
        return value.endsWith("/")?value.substring(0,value.length()-1):value;
    }

    private static String extractText(Map<?,?> response){
        if(response==null)return null;
        Object direct=response.get("output_text");
        if(direct instanceof String text&&!text.isBlank())return text;
        Object output=response.get("output");
        if(!(output instanceof List<?> items))return null;
        StringBuilder result=new StringBuilder();
        for(Object item:items){
            if(!(item instanceof Map<?,?> itemMap))continue;
            appendText(result,itemMap);
            Object content=itemMap.get("content");
            if(content instanceof List<?> parts){
                for(Object part:parts)if(part instanceof Map<?,?> partMap)appendText(result,partMap);
            }
        }
        return result.isEmpty()?null:result.toString();
    }

    private static void appendText(StringBuilder result,Map<?,?> value){
        Object type=value.get("type");
        Object text=value.get("text");
        if(text instanceof String content&&!content.isBlank()
                &&("output_text".equals(type)||"text".equals(type))){
            if(!result.isEmpty())result.append('\n');
            result.append(content);
        }
    }

    private static List<ModelSource> extractSources(Map<?,?> response){
        if(response==null)return List.of();
        Map<String,ModelSource> sources=new LinkedHashMap<>();
        collectSources(JSON.valueToTree(response),sources);
        return new ArrayList<>(sources.values()).stream().limit(8).toList();
    }

    private static void collectSources(JsonNode node,Map<String,ModelSource> sources){
        if(node==null||node.isNull()||sources.size()>=8)return;
        if(node.isObject()){
            String url=node.path("url").asText("");
            if((url.startsWith("https://")||url.startsWith("http://"))&&!sources.containsKey(url)){
                String title=node.path("title").asText("互联网来源");
                String excerpt=node.path("snippet").asText(node.path("text").asText("公开网络检索结果"));
                sources.put(url,new ModelSource(title.isBlank()?"互联网来源":title,
                        excerpt.isBlank()?"公开网络检索结果":excerpt,url));
            }
            node.elements().forEachRemaining(child->collectSources(child,sources));
        }else if(node.isArray()){
            node.elements().forEachRemaining(child->collectSources(child,sources));
        }
    }

    private static ModelProviderException providerFailure(RestClientResponseException exception){
        String code="UNKNOWN";
        String message="未返回错误说明";
        try {
            JsonNode body=JSON.readTree(exception.getResponseBodyAsString());
            JsonNode error=body.path("error");
            if(error.isMissingNode()||error.isNull())error=body;
            code=error.path("code").asText(code);
            message=error.path("message").asText(message);
        } catch (Exception ignored) {
            // 非 JSON 错误页也只透传 HTTP 状态，不记录响应正文。
        }
        return new ModelProviderException(exception.getStatusCode().value(),code,message,exception);
    }
}
