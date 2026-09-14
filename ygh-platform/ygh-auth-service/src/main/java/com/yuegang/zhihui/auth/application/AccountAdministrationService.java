package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.common.redis.SessionStateStore;

public final class AccountAdministrationService {
    private final AccountAdministrationRepository repository; private final SessionStateStore sessions;
    public AccountAdministrationService(AccountAdministrationRepository repository,SessionStateStore sessions){this.repository=repository;this.sessions=sessions;}
    public AccountStatusResponse change(String targetUserId,ChangeAccountStatusRequest request,long operatorUserId){
        long target=parse(targetUserId);if(target==operatorUserId&&request.status()==ChangeAccountStatusRequest.AccountStatusCommand.DISABLED)throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        if(request.reason()!=null&&request.reason().length()>500)throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        AccountStatus status=AccountStatus.valueOf(request.status().name());
        var result=repository.changeStatus(target,status,request.version(),operatorUserId,request.reason()).orElseThrow(()->new BusinessException(ErrorCode.BUSINESS_CONFLICT));
        if(status==AccountStatus.DISABLED)sessions.disableAccount(result.accountId());else sessions.enableAccount(result.accountId());
        return new AccountStatusResponse(Long.toString(result.userId()),result.status().name(),result.version(),result.updatedAt());
    }
    private static long parse(String value){try{long id=Long.parseLong(value);if(id<=0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new BusinessException(ErrorCode.VALIDATION_ERROR);}}
}
