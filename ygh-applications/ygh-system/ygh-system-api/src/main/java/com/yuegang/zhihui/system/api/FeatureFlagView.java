package com.yuegang.zhihui.system.api;
public record FeatureFlagView(String key,boolean enabled,int rolloutPercent,String rulesJson,long version){}
