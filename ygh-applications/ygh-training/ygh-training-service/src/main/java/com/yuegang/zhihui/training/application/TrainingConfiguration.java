package com.yuegang.zhihui.training.application;
import com.fasterxml.jackson.databind.ObjectMapper;import com.yuegang.zhihui.training.infrastructure.*;import com.yuegang.zhihui.training.security.TrainingUserResolver;import java.util.*;import javax.sql.DataSource;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)class TrainingConfiguration{
 @Bean TrainingProgressService trainingProgressService(DataSource dataSource){return new TrainingProgressService(dataSource);}
 @Bean TrainingQuizService trainingQuizService(DataSource dataSource,ObjectMapper json){return new TrainingQuizService(dataSource,json);}
 @Bean TrainingLearningRecordService trainingLearningRecordService(DataSource dataSource,ObjectMapper json){return new TrainingLearningRecordService(dataSource,json);}
 @Bean TrainingDocumentProgressService trainingDocumentProgressService(DataSource dataSource){return new TrainingDocumentProgressService(dataSource);}
 @Bean TrainingAssignmentService trainingAssignmentService(DataSource dataSource,ObjectMapper json){return new TrainingAssignmentService(dataSource,json);}
 @Bean ScopedAssignmentService scopedAssignmentService(TrainingAssignmentService assignments,OrganizationTargetClient targets){return new ScopedAssignmentService(assignments,targets);}
 @Bean OrganizationTargetClient organizationTargetClient(@Value("${ygh.training.user-base-url}")String base,@Value("${ygh.internal-request.hmac-base64}")String encoded,ObjectMapper json){byte[]key=Base64.getDecoder().decode(encoded);try{return new OrganizationTargetClient(base,key,json);}finally{Arrays.fill(key,(byte)0);}}
 @Bean LearningPathService learningPathService(DataSource dataSource){return new LearningPathService(dataSource);}
 @Bean TrainingContentService trainingContentService(DataSource dataSource,ObjectMapper json,@Value("${ygh.training.storage-root}")String root){return new TrainingContentService(dataSource,json,root);}
 @Bean TrainingCatalogQueryService trainingCatalogQueryService(DataSource dataSource){return new TrainingCatalogQueryService(dataSource);}
 @Bean TrainingAccessGuard trainingAccessGuard(DataSource dataSource){return new TrainingAccessGuard(dataSource);}
 @Bean TrainingUserResolver trainingUserResolver(@Value("${ygh.internal-request.hmac-base64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new TrainingUserResolver(key);}finally{Arrays.fill(key,(byte)0);}}
}
