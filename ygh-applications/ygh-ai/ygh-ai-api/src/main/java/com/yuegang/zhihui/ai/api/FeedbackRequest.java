package com.yuegang.zhihui.ai.api;import jakarta.validation.constraints.*;public record FeedbackRequest(@NotBlank String messageId,@NotNull Boolean helpful,@Size(max=1000)String comment){}
