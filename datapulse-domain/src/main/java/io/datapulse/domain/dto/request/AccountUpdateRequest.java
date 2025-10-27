package io.datapulse.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
    
    @NotBlank(message = "{validation.account.name.notBlank}")
    @Size(max = 255, message = "{validation.account.name.maxSize}")
    String name

) {

}
