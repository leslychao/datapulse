package io.datapulse.promotions.adapter.ozon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datapulse.integration.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OzonPromoWriteAdapter {

  private static final String ACTIVATE_PATH = "/v1/actions/products/activate";
  private static final String DEACTIVATE_PATH = "/v1/actions/products/deactivate";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties integrationProperties;
  private final ObjectMapper objectMapper;

  public PromoWriteResult activateProducts(String clientId, String apiKey,
                                            long externalActionId,
                                            List<ActivateProductRequest> products) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("action_id", externalActionId);
    ArrayNode productsArray = body.putArray("products");
    for (ActivateProductRequest product : products) {
      ObjectNode node = productsArray.addObject();
      node.put("product_id", product.productId());
      node.put("action_price", product.actionPrice().doubleValue());
      if (product.stock() != null) {
        node.put("stock", product.stock());
      }
    }

    return executeWrite(clientId, apiKey, ACTIVATE_PATH, body);
  }

  public PromoWriteResult deactivateProducts(String clientId, String apiKey,
                                              long externalActionId,
                                              List<Long> productIds) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("action_id", externalActionId);
    ArrayNode idsArray = body.putArray("product_ids");
    for (Long productId : productIds) {
      idsArray.add(productId);
    }

    return executeWrite(clientId, apiKey, DEACTIVATE_PATH, body);
  }

  private PromoWriteResult executeWrite(String clientId, String apiKey,
                                         String path, ObjectNode body) {
    String baseUrl = integrationProperties.getOzon().getSellerBaseUrl();
    try {
      String responseBody = webClientBuilder.build()
          .post()
          .uri(baseUrl + path)
          .header("Client-Id", clientId)
          .header("Api-Key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body.toString())
          .retrieve()
          .bodyToMono(String.class)
          .timeout(TIMEOUT)
          .block();

      return parseResponse(responseBody);
    } catch (Exception e) {
      log.error("Ozon promo write failed: path={}, request={}, error={}",
          path, body.toString(), e.getMessage(), e);
      throw e;
    }
  }

  private PromoWriteResult parseResponse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode result = root.path("result");

      List<Long> acceptedIds = new ArrayList<>();
      JsonNode productIdsNode = result.path("product_ids");
      if (productIdsNode.isArray()) {
        for (JsonNode id : productIdsNode) {
          acceptedIds.add(id.asLong());
        }
      }

      List<PromoWriteResult.RejectedProduct> rejected = new ArrayList<>();
      JsonNode rejectedNode = result.path("rejected");
      if (rejectedNode.isArray()) {
        for (JsonNode item : rejectedNode) {
          rejected.add(new PromoWriteResult.RejectedProduct(
              item.path("product_id").asLong(),
              item.path("reason").asText("")));
        }
      }

      return new PromoWriteResult(acceptedIds, rejected, responseBody);
    } catch (Exception e) {
      log.error("Failed to parse Ozon promo write response: response={}, error={}",
          responseBody, e.getMessage(), e);
      return new PromoWriteResult(List.of(), List.of(), responseBody);
    }
  }

  public record ActivateProductRequest(
      long productId,
      BigDecimal actionPrice,
      Integer stock) {}

  public record PromoWriteResult(
      List<Long> acceptedProductIds,
      List<RejectedProduct> rejected,
      String rawResponse
  ) {

    public boolean isAccepted(long productId) {
      return acceptedProductIds.contains(productId);
    }

    public record RejectedProduct(long productId, String reason) {}
  }
}
