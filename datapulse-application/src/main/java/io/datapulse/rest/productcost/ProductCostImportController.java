package io.datapulse.rest.productcost;

import io.datapulse.core.service.productcost.ProductCostService;
import io.datapulse.domain.response.productcost.ProductCostImportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(
    value = "/api/accounts/{accountId}/product-costs",
    produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
@Validated
public class ProductCostImportController {

  private final ProductCostService productCostService;

  @PostMapping(
      path = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ProductCostImportResponse importFromExcel(
      @PathVariable("accountId")
      Long accountId,
      @RequestPart("file")
      MultipartFile file
  ) {
    return productCostService.importFromExcel(accountId, file);
  }
}
