package com.yuegang.zhihui.training.api;import jakarta.validation.constraints.*;public record AddPathCourseRequest(@NotBlank String courseId,@Positive int sequenceNo,String prerequisiteCourseId){}
