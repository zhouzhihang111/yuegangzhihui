package com.yuegang.zhihui.user.application;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.AddressRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AddressServiceTest {
    @Test void delegatesOnlyParsedOwnerIdsAndMapsConflicts(){
        var repo=new MemoryRepository();
        var service=new AddressService(repo,new UserIdGenerator(2,Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"),ZoneOffset.UTC)));
        var request=new CreateAddressRequest(null,"A","123456","CN",null,"P","C","D","detail",null,false);
        assertThat(service.create("7",request).id()).isNotBlank(); assertThat(repo.owner).isEqualTo(7);
        assertThat(service.list("7")).hasSize(1);
        var update=new UpdateAddressRequest(null,"A","123456","CN",null,"P","C","D","detail",null,true,0);
        assertThat(service.update("7","9",update).id()).isEqualTo("9");
        assertThat(service.makeDefault("7","9",0).defaultAddress()).isTrue();
        assertThat(service.delete("7","9",0).completed()).isTrue();
        repo.accept=false;
        assertThatThrownBy(()->service.update("7","9",update)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.makeDefault("7","9",0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.delete("7","9",0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.list("bad")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.list("0")).isInstanceOf(BusinessException.class);
    }
    private static final class MemoryRepository implements AddressRepository{
        long owner; boolean accept=true; AddressView value=new AddressView("9",null,"A","123456","CN",null,"P","C","D","detail",null,true,0,OffsetDateTime.now());
        public List<AddressView> findAll(long userId){owner=userId;return List.of(value);}
        public AddressView create(long id,long userId,CreateAddressRequest request){owner=userId;return new AddressView(Long.toString(id),null,"A","123456","CN",null,"P","C","D","detail",null,true,0,OffsetDateTime.now());}
        public Optional<AddressView> update(long id,long userId,UpdateAddressRequest request){return accept?Optional.of(value):Optional.empty();}
        public boolean delete(long id,long userId,long version){return accept;}
        public Optional<AddressView> makeDefault(long id,long userId,long version){return accept?Optional.of(value):Optional.empty();}
    }
}
