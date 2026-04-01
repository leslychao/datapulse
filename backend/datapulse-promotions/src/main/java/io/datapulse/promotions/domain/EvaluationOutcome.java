package io.datapulse.promotions.domain;

import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoDecisionEntity;
import io.datapulse.promotions.persistence.PromoEvaluationEntity;

record EvaluationOutcome(PromoEvaluationEntity evaluation,
                          PromoDecisionEntity decision,
                          PromoActionEntity action,
                          PromoDecisionType decisionType) {
}
