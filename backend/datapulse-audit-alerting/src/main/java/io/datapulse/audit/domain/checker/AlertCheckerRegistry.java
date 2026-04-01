package io.datapulse.audit.domain.checker;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class AlertCheckerRegistry {

    private final Map<String, AlertChecker> checkersByType;

    public AlertCheckerRegistry(List<AlertChecker> checkers) {
        this.checkersByType = checkers.stream()
                .collect(Collectors.toUnmodifiableMap(AlertChecker::ruleType, Function.identity()));
    }

    public AlertChecker getChecker(String ruleType) {
        return checkersByType.get(ruleType);
    }

    public boolean hasChecker(String ruleType) {
        return checkersByType.containsKey(ruleType);
    }
}
