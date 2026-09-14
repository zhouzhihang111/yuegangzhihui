package com.yuegang.zhihui.search.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.search.api.*;
import com.yuegang.zhihui.search.infrastructure.DoubaoEmbeddingGateway;
import com.yuegang.zhihui.search.infrastructure.EmbeddingGateway;
import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import java.sql.Timestamp;import java.time.LocalDateTime;import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.*;

class SearchServicesTest {
  HttpServer server; String base; AtomicReference<String> lastKnowledgeSearch=new AtomicReference<>();AtomicReference<String> lastIndexPath=new AtomicReference<>();
  @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);base="http://localhost:"+server.getAddress().getPort();server.createContext("/embeddings",x->{byte[] b="{\"data\":[{\"embedding\":[0.1,0.2]}]}".getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.createContext("/knowledge-active/_search",x->{lastKnowledgeSearch.set(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String content="x".repeat(301);byte[] b=("{\"hits\":{\"hits\":[{\"_score\":2.0,\"_source\":{\"documentId\":\"1\",\"chunkId\":\"2\",\"title\":\"Policy\",\"content\":\""+content+"\"}}]}}" ).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.createContext("/product-active/_search",x->{byte[] b="{\"hits\":{\"hits\":[{\"_score\":3.5,\"_source\":{\"documentId\":\"product:42\"}},{\"_score\":99,\"_source\":{\"documentId\":\"knowledge:1\"}}]}}".getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().add("Content-Type","application/json");x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.createContext("/",x->{lastIndexPath.set(x.getRequestURI().getPath());x.sendResponseHeaders(200,-1);x.close();});server.start();}
  @AfterEach void stop(){server.stop(0);}
  @Test void providesStableDevelopmentEmbeddingWithoutCredential(){var gateway=new DoubaoEmbeddingGateway(base,"","model");assertThat(gateway.configured()).isFalse();assertThat(gateway.embed("policy")).hasSize(1024).isEqualTo(gateway.embed("policy"));}
  @Test void embeddingDeletionAndIndexLifecycle() throws Exception {
    var embedding=new DoubaoEmbeddingGateway(base,"secret","model");assertThat(embedding.embed("policy")).zipSatisfy(java.util.List.of(0.1,0.2),(actual,expected)->assertThat(actual).isCloseTo(expected,within(1.0e-6)));
    JdbcTemplate jdbc=mock(JdbcTemplate.class);new SearchDeletionService(jdbc,base,"knowledge-active").deleteDocument("8");verify(jdbc).update("DELETE FROM search_embedding WHERE document_id=?","8");new SearchDeletionService(jdbc,base,"knowledge-active").deleteDocument("product:8","product-active");assertThatThrownBy(()->new SearchDeletionService(jdbc,base,"knowledge-active").deleteDocument("bad/path")).isInstanceOf(BusinessException.class);
    var lifecycle=new IndexLifecycleService(jdbc,base,"knowledge-active");lifecycle.create("knowledge-v2");assertThatThrownBy(()->lifecycle.create("INVALID")).isInstanceOf(BusinessException.class);
    when(jdbc.queryForObject(anyString(),eq(Long.class),eq("knowledge-v2"))).thenReturn(1L);when(jdbc.update(startsWith("UPDATE search_index_version"),eq("knowledge-v2"),eq("knowledge-active"))).thenReturn(1);
    doAnswer(i->{ResultSetExtractor<?> e=i.getArgument(1);var rs=mock(java.sql.ResultSet.class);when(rs.next()).thenReturn(true);when(rs.getString(1)).thenReturn("knowledge-active");when(rs.getString(2)).thenReturn("knowledge-v2");when(rs.getString(3)).thenReturn("knowledge-v1");when(rs.getLong(5)).thenReturn(2L);when(rs.getTimestamp(4)).thenReturn(Timestamp.valueOf(LocalDateTime.now()));return e.extractData(rs);}).when(jdbc).query(startsWith("SELECT alias_name"),any(ResultSetExtractor.class),eq("knowledge-active"));
    assertThat(lifecycle.switchTo(new SwitchIndexRequest("knowledge-v2",true)).activeVersion()).isEqualTo("knowledge-v2");lifecycle.deletePrevious();verify(jdbc).update("DELETE FROM search_embedding WHERE index_version=?","knowledge-v1");
  }
  @Test void hybridSearchesMergesAndIndexes(){JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(java.util.List.of());when(jdbc.queryForObject(anyString(),eq(String.class),eq("knowledge-active"))).thenReturn("knowledge-v1");var service=new HybridSearchService(jdbc,base,new DoubaoEmbeddingGateway(base,"secret","model"),"knowledge-active");assertThat(service.search(new SearchRequest("policy",5,"POLICY"))).singleElement().satisfies(h->{assertThat(h.title()).isEqualTo("Policy");assertThat(h.excerpt()).hasSize(300);});assertThat(lastKnowledgeSearch.get()).contains("visibility","category").doesNotContain("visibility.keyword","category.keyword");assertThat(service.search(new SearchRequest("policy",5,null))).hasSize(1);service.index(new IndexChunkCommand("1","2","t","c","POLICY","PUBLIC","knowledge-active",true));assertThat(lastIndexPath.get()).isEqualTo("/knowledge-v1/_doc/2");assertThatThrownBy(()->service.index(new IndexChunkCommand("1","2","t","c","POLICY","PUBLIC","knowledge-v1",false))).isInstanceOf(BusinessException.class);}
  @Test void fallsBackToLexicalSearchAndIndexWhenEmbeddingModelIsUnavailable(){
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    EmbeddingGateway unavailable=new EmbeddingGateway(){public java.util.List<Double> embed(String text){throw new IllegalStateException("model unavailable");}public boolean configured(){return true;}};
    var service=new HybridSearchService(jdbc,base,unavailable,"knowledge-active");
    assertThat(service.search(new SearchRequest("policy",5,"POLICY"))).singleElement().satisfies(hit->assertThat(hit.title()).isEqualTo("Policy"));
    service.index(new IndexChunkCommand("1","2","t","c","POLICY","PUBLIC","knowledge-v1",true));
    verify(jdbc,never()).update(startsWith("INSERT INTO search_embedding"),any(Object[].class));
  }
  @Test void reportsDependencyUnavailableWhenLexicalIndexIsDown(){
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    EmbeddingGateway unavailable=new EmbeddingGateway(){public java.util.List<Double> embed(String text){throw new IllegalStateException("model unavailable");}public boolean configured(){return true;}};
    var service=new HybridSearchService(jdbc,"http://localhost:1",unavailable,"knowledge-active");
    assertThatThrownBy(()->service.index(new IndexChunkCommand("1","2","t","c","POLICY","PUBLIC","knowledge-v1",true)))
        .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.errorCode()).isEqualTo(com.yuegang.zhihui.common.core.ErrorCode.DEPENDENCY_UNAVAILABLE));
  }
  @Test void productSearchReturnsOnlyNamespacedProductDocuments(){var service=new ProductFullTextSearchService(base,"product-active");assertThat(service.search(new ProductSearchRequest("荔枝",10))).singleElement().satisfies(hit->{assertThat(hit.skuId()).isEqualTo("42");assertThat(hit.score()).isEqualTo(3.5);});}
}
