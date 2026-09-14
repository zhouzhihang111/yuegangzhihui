package com.yuegang.zhihui.system.api;
import jakarta.validation.constraints.*;import java.util.Set;
public record AssignRolesRequest(@NotNull @Size(max=32) Set<@Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String> roleCodes,@PositiveOrZero long version,String reason){}
