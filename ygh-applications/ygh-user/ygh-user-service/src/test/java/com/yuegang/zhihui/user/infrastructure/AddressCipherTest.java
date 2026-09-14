package com.yuegang.zhihui.user.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AddressCipherTest {
    private static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    @Test void encryptsWithRandomIvAndBindsOwnerFieldAndVersion(){
        var cipher=new AddressCipher(KEY,1);
        byte[] first=cipher.encrypt(7,"recipientName","张三"); byte[] second=cipher.encrypt(7,"recipientName","张三");
        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first,StandardCharsets.UTF_8)).doesNotContain("张三");
        assertThat(cipher.decrypt(7,"recipientName",1,first)).isEqualTo("张三");
        assertThatThrownBy(()->cipher.decrypt(8,"recipientName",1,first)).isInstanceOf(IllegalStateException.class);
        first[first.length-1]^=1;
        assertThatThrownBy(()->cipher.decrypt(7,"recipientName",1,first)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsWeakKeysAndInvalidVersions(){
        assertThatThrownBy(()->new AddressCipher(Base64.getEncoder().encodeToString(new byte[16]),1)).isInstanceOf(IllegalArgumentException.class);
        var previous=new AddressCipher(KEY,1); byte[] value=previous.encrypt(1,"x","secret");
        var current=new AddressCipher(KEY,2);
        assertThat(current.decrypt(1,"x",1,value)).isEqualTo("secret");
        assertThatThrownBy(()->current.decrypt(1,"x",0,value)).isInstanceOf(IllegalStateException.class);
    }
}
