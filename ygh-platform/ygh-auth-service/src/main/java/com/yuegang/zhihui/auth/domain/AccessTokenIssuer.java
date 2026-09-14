package com.yuegang.zhihui.auth.domain;

public interface AccessTokenIssuer {
    AccessToken issue(TokenPrincipal principal);
}
