package io.datapulse.domain.dto.request;

import static io.datapulse.domain.ValidationKeys.ACCOUNT_NAME_MAX_LENGTH;
import static io.datapulse.domain.ValidationKeys.ACCOUNT_NAME_REQUIRED;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(

    @NotBlank(message = ACCOUNT_NAME_REQUIRED)
    @Size(max = 32, message = ACCOUNT_NAME_MAX_LENGTH)
    String name,
    Boolean active

) {

}
