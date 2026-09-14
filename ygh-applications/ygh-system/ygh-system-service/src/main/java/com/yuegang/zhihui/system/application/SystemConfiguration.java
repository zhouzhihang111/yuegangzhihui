package com.yuegang.zhihui.system.application;
import com.yuegang.zhihui.system.domain.*;import com.yuegang.zhihui.system.infrastructure.*;import com.yuegang.zhihui.system.security.*;import java.time.Clock;import java.util.*;import javax.sql.DataSource;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)class SystemConfiguration{
 @Bean AuthorizationRepository authorizationRepository(DataSource dataSource){return new JdbcAuthorizationRepository(dataSource);}
 @Bean AuthorizationService authorizationService(AuthorizationRepository repository){return new AuthorizationService(repository);}
 @Bean RoleAdministrationService roleAdministrationService(DataSource dataSource){return new RoleAdministrationService(dataSource);}
 @Bean SystemSettingService systemSettingService(DataSource dataSource){return new SystemSettingService(dataSource);}
 @Bean SystemSecretCipher systemSecretCipher(@Value("${ygh.system.config-master-key-base64}")String encoded){byte[]key=Base64.getDecoder().decode(encoded);try{return new SystemSecretCipher(key);}finally{Arrays.fill(key,(byte)0);}}
 @Bean AiProviderConfigService aiProviderConfigService(DataSource dataSource,SystemSecretCipher secrets){return new AiProviderConfigService(dataSource,secrets);}
 @Bean SystemCatalogService systemCatalogService(DataSource dataSource){return new SystemCatalogService(dataSource);}
 @Bean SystemDictionaryAdministrationService systemDictionaryAdministrationService(DataSource dataSource){return new SystemDictionaryAdministrationService(dataSource);}
 @Bean SystemTrustedUserContextResolver systemTrustedUserContextResolver(@Value("${ygh.internal-request.hmac-base64}")String encoded,Clock clock){byte[]secret=Base64.getDecoder().decode(encoded);try{return new SystemTrustedUserContextResolver(secret,clock);}finally{Arrays.fill(secret,(byte)0);}}
 @Bean InternalServiceVerifier internalServiceVerifier(@Value("${ygh.internal-request.hmac-base64}")String encoded,Clock clock){byte[]secret=Base64.getDecoder().decode(encoded);try{return new InternalServiceVerifier(secret,clock);}finally{Arrays.fill(secret,(byte)0);}}
 @Bean Clock systemClock(){return Clock.systemUTC();}
}
