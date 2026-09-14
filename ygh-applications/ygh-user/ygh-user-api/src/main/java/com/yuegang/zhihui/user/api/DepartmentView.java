package com.yuegang.zhihui.user.api;

public record DepartmentView(String id,String parentId,String code,String name,int sortOrder,boolean enabled,long version) {}
