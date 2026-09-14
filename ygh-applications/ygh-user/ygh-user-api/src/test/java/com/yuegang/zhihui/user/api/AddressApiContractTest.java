package com.yuegang.zhihui.user.api;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AddressApiContractTest {
    @Test void createAndUpdateContractsExposeFieldsWithoutLeakingPiiInLogs(){
        var create=new CreateAddressRequest("家","张三","13800138000","CN","44","广东省","广州市","天河区","路1号","510000",true);
        assertThat(create.label()).isEqualTo("家"); assertThat(create.recipientName()).isEqualTo("张三");
        assertThat(create.recipientPhone()).isEqualTo("13800138000"); assertThat(create.countryCode()).isEqualTo("CN");
        assertThat(create.provinceCode()).isEqualTo("44"); assertThat(create.provinceName()).isEqualTo("广东省");
        assertThat(create.cityName()).isEqualTo("广州市"); assertThat(create.districtName()).isEqualTo("天河区");
        assertThat(create.addressDetail()).isEqualTo("路1号"); assertThat(create.postalCode()).isEqualTo("510000");
        assertThat(create.defaultAddress()).isTrue(); assertThat(create.toString()).doesNotContain("张三","13800138000","路1号");
        var update=new UpdateAddressRequest("公司","李四","13900139000","CN",null,"广东省","深圳市","南山区","路2号",null,false,3);
        assertThat(update.label()).isEqualTo("公司"); assertThat(update.recipientName()).isEqualTo("李四");
        assertThat(update.recipientPhone()).isEqualTo("13900139000"); assertThat(update.countryCode()).isEqualTo("CN");
        assertThat(update.provinceCode()).isNull(); assertThat(update.provinceName()).isEqualTo("广东省");
        assertThat(update.cityName()).isEqualTo("深圳市"); assertThat(update.districtName()).isEqualTo("南山区");
        assertThat(update.addressDetail()).isEqualTo("路2号"); assertThat(update.postalCode()).isNull();
        assertThat(update.defaultAddress()).isFalse(); assertThat(update.version()).isEqualTo(3);
        assertThat(update.toString()).doesNotContain("李四","13900139000","路2号");
    }
    @Test void viewAndOperationContractsKeepIdsAsStringsAndTimeZoned(){
        var now=OffsetDateTime.parse("2026-07-12T10:00:00+08:00");
        var view=new AddressView("9","家","张三","13800138000","CN","44","广东省","广州市","天河区","路1号","510000",true,2,now);
        assertThat(view.id()).isEqualTo("9"); assertThat(view.label()).isEqualTo("家"); assertThat(view.recipientName()).isEqualTo("张三");
        assertThat(view.recipientPhone()).isEqualTo("13800138000"); assertThat(view.countryCode()).isEqualTo("CN");
        assertThat(view.provinceCode()).isEqualTo("44"); assertThat(view.provinceName()).isEqualTo("广东省");
        assertThat(view.cityName()).isEqualTo("广州市"); assertThat(view.districtName()).isEqualTo("天河区");
        assertThat(view.addressDetail()).isEqualTo("路1号"); assertThat(view.postalCode()).isEqualTo("510000");
        assertThat(view.defaultAddress()).isTrue(); assertThat(view.version()).isEqualTo(2); assertThat(view.updatedAt()).isEqualTo(now);
        assertThat(new AddressOperationResponse(true).completed()).isTrue();
    }
}
