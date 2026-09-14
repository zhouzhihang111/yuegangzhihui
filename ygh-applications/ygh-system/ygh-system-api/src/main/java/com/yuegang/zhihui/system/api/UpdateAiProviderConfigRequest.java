package com.yuegang.zhihui.system.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAiProviderConfigRequest(
        @NotBlank @Pattern(regexp = "DOUBAO_ARK") String provider,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{3,200}") String chatModel,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{3,200}") String embeddingModel,
        boolean webSearchEnabled,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 4096) String apiKey,
        long version) {
}
