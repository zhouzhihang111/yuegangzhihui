package com.yuegang.zhihui.user.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.user.api.*;
import java.sql.DriverManager;
import java.util.Base64;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAddressRepositoryIntegrationTest {
    @Test void enforcesOwnershipOptimisticLockDefaultAndEncryptedStorage() throws Exception {
        try(var mysql=YghTestContainerFactory.mysql().start()){
            Flyway.configure().dataSource(mysql.jdbcUrl(),mysql.username(),mysql.credential()).locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(mysql.jdbcUrl(),mysql.username(),mysql.credential())){
                c.createStatement().executeUpdate("INSERT INTO user_profile(user_id,display_name) VALUES(1,'A'),(2,'B')");
            }
            var ds=new DriverManagerDataSource(mysql.jdbcUrl(),mysql.username(),mysql.credential());
            var repository=new JdbcAddressRepository(ds,new AddressCipher(Base64.getEncoder().encodeToString(new byte[32]),1));
            var home=create("家",false,"天河路1号");
            AddressView first=repository.create(10,1,home);
            assertThat(first.defaultAddress()).isTrue();
            AddressView second=repository.create(11,1,create("公司",true,"珠江路2号"));
            assertThat(second.defaultAddress()).isTrue();
            assertThat(repository.findAll(1)).extracting(AddressView::id).containsExactly("11","10");
            assertThat(repository.findAll(1)).filteredOn(AddressView::defaultAddress).hasSize(1);
            assertThat(repository.update(11,2,update(second,0))).isEmpty();
            assertThat(repository.update(11,1,update(second,99))).isEmpty();
            AddressView changed=repository.update(11,1,update(second,second.version())).orElseThrow();
            assertThat(changed.version()).isGreaterThan(second.version());
            assertThat(changed.defaultAddress()).isFalse();
            assertThat(repository.findAll(1)).filteredOn(AddressView::defaultAddress).singleElement().extracting(AddressView::id).isEqualTo("10");
            assertThat(repository.delete(11,2,changed.version())).isFalse();
            assertThat(repository.delete(11,1,changed.version())).isTrue();
            assertThat(repository.findAll(1)).singleElement().satisfies(a->assertThat(a.defaultAddress()).isTrue());
            try(var c=DriverManager.getConnection(mysql.jdbcUrl(),mysql.username(),mysql.credential());var rs=c.createStatement().executeQuery("SELECT HEX(address_detail_ciphertext) value FROM user_address WHERE id=10")){
                assertThat(rs.next()).isTrue(); assertThat(rs.getString("value")).doesNotContain("天河路1号");
            }
        }
    }
    private static CreateAddressRequest create(String label,boolean preferred,String detail){return new CreateAddressRequest(label,"张三","13800138000","CN","440000","广东省","广州市","天河区",detail,"510000",preferred);}
    private static UpdateAddressRequest update(AddressView a,long version){return new UpdateAddressRequest(a.label(),a.recipientName(),a.recipientPhone(),a.countryCode(),a.provinceCode(),a.provinceName(),a.cityName(),a.districtName(),a.addressDetail(),a.postalCode(),false,version);}
}
