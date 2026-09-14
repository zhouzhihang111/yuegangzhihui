package com.yuegang.zhihui.system.api;
import jakarta.validation.constraints.*;
public record UpdateFeatureFlagRequest(boolean enabled,@Min(0) @Max(100) int rolloutPercent,String rulesJson,long version){}
