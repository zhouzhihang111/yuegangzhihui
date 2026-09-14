package com.yuegang.zhihui.search.infrastructure;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 豆包 OpenAI-compatible embedding adapter implemented through LangChain4j. */
public final class DoubaoEmbeddingGateway implements EmbeddingGateway {
    private final EmbeddingModel model;
    public DoubaoEmbeddingGateway(String baseUrl,String apiKey,String modelName){
        model=apiKey==null||apiKey.isBlank()?null:OpenAiEmbeddingModel.builder().baseUrl(baseUrl).apiKey(apiKey).modelName(modelName)
                .timeout(Duration.ofSeconds(60)).maxRetries(1).logRequests(false).logResponses(false).build();
    }
    @Override public List<Double> embed(String text){
        if(model==null)return developmentVector(text);
        float[] vector=model.embed(text).content().vector();
        List<Double> result=new ArrayList<>(vector.length);
        for(float value:vector)result.add((double)value);
        return List.copyOf(result);
    }
    @Override public boolean configured(){return model!=null;}
    private static List<Double> developmentVector(String text){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            List<Double> vector=new ArrayList<>(1024);
            for(int i=0;i<1024;i++)vector.add((digest[i%digest.length]&0xff)/255.0);
            return List.copyOf(vector);
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
