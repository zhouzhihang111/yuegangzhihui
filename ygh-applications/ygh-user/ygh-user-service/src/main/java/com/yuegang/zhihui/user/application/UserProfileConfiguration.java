package com.yuegang.zhihui.user.application;

import com.yuegang.zhihui.user.domain.UserProfileRepository;
import com.yuegang.zhihui.user.infrastructure.JdbcUserProfileRepository;
import javax.sql.DataSource;
import com.yuegang.zhihui.user.infrastructure.*;
import com.yuegang.zhihui.user.domain.AddressRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
class UserProfileConfiguration {
    @Bean UserProfileRepository userProfileRepository(DataSource dataSource, AddressCipher cipher) { return new JdbcUserProfileRepository(dataSource, cipher); }
    @Bean UserProfileService userProfileService(UserProfileRepository repository) { return new UserProfileService(repository); }
    @Bean AddressCipher addressCipher(@Value("${ygh.user.pii-key-base64}") String key,
            @Value("${ygh.user.pii-key-version:1}") int version) { return new AddressCipher(key, version); }
    @Bean AddressRepository addressRepository(DataSource dataSource, AddressCipher cipher) { return new JdbcAddressRepository(dataSource, cipher); }
    @Bean UserIdGenerator userIdGenerator(@Value("${ygh.user.id-worker:2}") long worker) { return new UserIdGenerator(worker, Clock.systemUTC()); }
    @Bean AddressService addressService(AddressRepository repository, UserIdGenerator ids) { return new AddressService(repository, ids); }
    @Bean OrganizationService organizationService(DataSource dataSource, UserIdGenerator ids) { return new OrganizationService(dataSource, ids); }
}
