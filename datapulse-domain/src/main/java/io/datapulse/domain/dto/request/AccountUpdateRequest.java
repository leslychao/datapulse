package io.datapulse.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(

    @NotNull(message = "${validation.account.id.not-null}")
    Long id,

    @NotBlank(message = "${validation.account.name.not-blank}")
    @Size(max = 255, message = "${validation.account.name.max-length}")
    String name

) {

}
