package com.yuegang.zhihui.training.api;import jakarta.validation.constraints.*;public record SaveLearningPathRequest(@NotBlank @Size(max=64)String positionCode,@NotBlank @Size(max=200)String name){}
