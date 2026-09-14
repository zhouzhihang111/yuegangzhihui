package com.yuegang.zhihui.user.api;

import java.time.LocalDate;
import java.util.Set;

public record EmployeeView(String id,String userId,String employeeNo,String departmentId,Set<String> positionIds,String status,LocalDate hiredOn,long version) {}
