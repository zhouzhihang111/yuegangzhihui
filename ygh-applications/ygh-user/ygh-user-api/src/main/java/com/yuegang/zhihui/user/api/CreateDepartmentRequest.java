package com.yuegang.zhihui.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(String parentId,@NotBlank @Size(max=32) String code,@NotBlank @Size(max=100) String name,int sortOrder) {}
