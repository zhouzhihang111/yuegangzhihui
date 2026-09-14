package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountAdministrationServiceTest {
    @Test void changesStatusByAccountIdAndRejectsUnsafeCommands(){
        var repository=new StubRepository();var sessions=new Sessions();var service=new AccountAdministrationService(repository,sessions);
        assertThat(service.change("20",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.DISABLED,3,"risk"),10).status()).isEqualTo("DISABLED");
        assertThat(sessions.disabled).isEqualTo(99);
        service.change("20",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.ACTIVE,4,null),10);
        assertThat(sessions.enabled).isEqualTo(99);
        assertThatThrownBy(()->service.change("10",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.DISABLED,0,"x"),10)).isInstanceOf(BusinessException.class);
        repository.accept=false;
        assertThatThrownBy(()->service.change("20",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.ACTIVE,0,"x"),10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.change("bad",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.ACTIVE,0,"x"),10)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.change("20",new ChangeAccountStatusRequest(ChangeAccountStatusRequest.AccountStatusCommand.ACTIVE,0,"x".repeat(501)),10)).isInstanceOf(BusinessException.class);
    }
    private static final class StubRepository implements AccountAdministrationRepository{
        boolean accept=true;public Optional<StatusChange> changeStatus(long userId,AccountStatus status,long version,long operator,String reason){return accept?Optional.of(new StatusChange(99,userId,status,version+1,OffsetDateTime.now())):Optional.empty();}
    }
    private static final class Sessions implements SessionStateStore{
        long disabled,enabled;public void register(long a,String j,Instant e,Instant n){}public void revoke(long a,String j,Instant e,Instant n){}public void disableAccount(long a){disabled=a;}public void enableAccount(long a){enabled=a;}
    }
}
