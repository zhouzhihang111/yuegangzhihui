package com.yuegang.zhihui.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public record CreateEmployeeRequest(@NotBlank String userId,@NotBlank @Size(max=32) String employeeNo,String departmentId,Set<String> positionIds,LocalDate hiredOn) {}
