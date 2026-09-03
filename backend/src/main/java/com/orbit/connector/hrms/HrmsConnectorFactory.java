package com.orbit.connector.hrms;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registry of all HrmsConnector beans, keyed by providerKey. */
@Component
public class HrmsConnectorFactory {

    private final Map<String, HrmsConnector> byKey;

    public HrmsConnectorFactory(List<HrmsConnector> connectors) {
        this.byKey = connectors.stream()
            .collect(Collectors.toUnmodifiableMap(HrmsConnector::providerKey, Function.identity()));
    }

    public Optional<HrmsConnector> byKey(String providerKey) {
        return providerKey == null ? Optional.empty() : Optional.ofNullable(byKey.get(providerKey));
    }

    public List<HrmsConnector> all() {
        return byKey.values().stream()
            .sorted(java.util.Comparator.comparing(HrmsConnector::displayName))
            .toList();
    }
}
