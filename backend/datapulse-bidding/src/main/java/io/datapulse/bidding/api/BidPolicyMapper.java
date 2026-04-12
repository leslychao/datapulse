package io.datapulse.bidding.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidPolicyAssignmentEntity;
import io.datapulse.bidding.persistence.BidPolicyEntity;
import io.datapulse.bidding.persistence.BiddingRunEntity;
import io.datapulse.bidding.persistence.ManualBidLockEntity;
import io.datapulse.platform.config.BaseMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(config = BaseMapperConfig.class)
public abstract class BidPolicyMapper {

  @Autowired
  private ObjectMapper objectMapper;

  @Mapping(source = "entity.id", target = "id")
  @Mapping(source = "entity.name", target = "name")
  @Mapping(source = "entity.strategyType", target = "strategyType")
  @Mapping(source = "entity.executionMode", target = "executionMode")
  @Mapping(source = "entity.status", target = "status")
  @Mapping(source = "entity.createdAt", target = "createdAt")
  @Mapping(source = "entity.updatedAt", target = "updatedAt")
  @Mapping(source = "assignmentCount", target = "assignmentCount")
  public abstract BidPolicySummaryResponse toSummary(BidPolicyEntity entity, int assignmentCount);

  @Mapping(source = "entity.id", target = "id")
  @Mapping(source = "entity.name", target = "name")
  @Mapping(source = "entity.strategyType", target = "strategyType")
  @Mapping(source = "entity.executionMode", target = "executionMode")
  @Mapping(source = "entity.status", target = "status")
  @Mapping(source = "entity.config", target = "config", qualifiedByName = "jsonStringToNode")
  @Mapping(source = "entity.createdBy", target = "createdBy")
  @Mapping(source = "entity.version", target = "version")
  @Mapping(source = "entity.createdAt", target = "createdAt")
  @Mapping(source = "entity.updatedAt", target = "updatedAt")
  @Mapping(source = "assignmentCount", target = "assignmentCount")
  public abstract BidPolicyDetailResponse toDetail(BidPolicyEntity entity, int assignmentCount);

  @Mapping(source = "assignmentScope", target = "scope")
  public abstract AssignmentResponse toResponse(BidPolicyAssignmentEntity entity);

  public abstract ManualBidLockResponse toResponse(ManualBidLockEntity entity);

  @Mapping(source = "signalSnapshot", target = "signalSnapshot", qualifiedByName = "jsonStringToNode")
  @Mapping(source = "guardsApplied", target = "guardsApplied", qualifiedByName = "jsonStringToNode")
  @Mapping(source = "explanationArgs", target = "explanationArgs", qualifiedByName = "jsonStringToNode")
  public abstract BidDecisionDetailResponse toDetail(BidDecisionEntity entity);

  @Mapping(source = "explanationArgs", target = "explanationArgs", qualifiedByName = "jsonStringToNode")
  public abstract BidDecisionSummaryResponse toSummary(BidDecisionEntity entity);

  public abstract BiddingRunSummaryResponse toSummary(BiddingRunEntity entity);

  @Named("jsonStringToNode")
  JsonNode jsonStringToNode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
