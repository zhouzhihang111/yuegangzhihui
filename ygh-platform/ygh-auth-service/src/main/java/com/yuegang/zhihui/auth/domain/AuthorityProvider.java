package com.yuegang.zhihui.auth.domain;
import java.util.Set;
public interface AuthorityProvider{Authorities find(long userId);record Authorities(Set<String>roles,Set<String>permissions){public Authorities{roles=Set.copyOf(roles);permissions=Set.copyOf(permissions);}}}
