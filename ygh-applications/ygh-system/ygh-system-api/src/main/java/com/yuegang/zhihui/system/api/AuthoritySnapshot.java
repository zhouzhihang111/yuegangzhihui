package com.yuegang.zhihui.system.api;
import java.util.Set;
public record AuthoritySnapshot(String userId,Set<String> roles,Set<String> permissions,long version){public AuthoritySnapshot{roles=Set.copyOf(roles);permissions=Set.copyOf(permissions);}}
