package com.yuegang.zhihui.system.api;
import jakarta.validation.constraints.*;import java.util.Set;
public record UpsertRoleRequest(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,@NotBlank @Size(max=100) String name,@NotNull Set<@Pattern(regexp="[A-Za-z][A-Za-z0-9:_-]{0,127}") String> permissions,boolean enabled,@PositiveOrZero long version){}
