package com.yuegang.zhihui.system.domain;
import com.yuegang.zhihui.system.api.*;import java.util.Optional;
public interface AuthorizationRepository{AuthoritySnapshot snapshot(long userId);Optional<AuthoritySnapshot> replaceRoles(long userId,long expectedVersion,java.util.Set<String> roles,long operator,String reason);}
