package com.yuegang.zhihui.auth.application;
import com.yuegang.zhihui.auth.domain.*;
public final class AuthorityAwareAccessTokenIssuer implements AccessTokenIssuer{private final AccessTokenIssuer delegate;private final AuthorityProvider provider;public AuthorityAwareAccessTokenIssuer(AccessTokenIssuer d,AuthorityProvider p){delegate=d;provider=p;}public AccessToken issue(TokenPrincipal p){var a=provider.find(p.userId());return delegate.issue(new TokenPrincipal(p.accountId(),p.userId(),a.roles(),a.permissions()));}}
