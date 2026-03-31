package io.datapulse.etl.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "datapulse.clickhouse.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ClickHouseMigrationRunner implements ApplicationRunner {

    private static final String BOOTSTRAP_DDL = """
            CREATE TABLE IF NOT EXISTS _schema_version (
                version    UInt32,
                script_name String,
                applied_at  DateTime DEFAULT now(),
                checksum    String
            ) ENGINE = MergeTree()
            ORDER BY version
            """;

    private static final String SELECT_APPLIED = "SELECT script_name, checksum FROM _schema_version";

    private static final String INSERT_VERSION = """
            INSERT INTO _schema_version (version, script_name, checksum)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate clickhouseJdbcTemplate;
    private final ClickHouseMigrationProperties properties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("ClickHouse migration started: scriptsLocation={}", properties.scriptsLocation());

        bootstrapSchemaVersionTable();

        var applied = loadAppliedMigrations();
        var scripts = discoverScripts();

        int appliedCount = 0;
        int skippedCount = 0;

        for (Resource script : scripts) {
            String scriptName = script.getFilename();
            String content = script.getContentAsString(StandardCharsets.UTF_8);
            String checksum = sha256(content);

            if (applied.containsKey(scriptName)) {
                verifyChecksum(scriptName, applied.get(scriptName), checksum);
                skippedCount++;
                continue;
            }

            applyScript(scriptName, content, checksum);
            appliedCount++;
        }

        log.info("ClickHouse migration completed: applied={}, skipped={}", appliedCount, skippedCount);
    }

    private void bootstrapSchemaVersionTable() {
        clickhouseJdbcTemplate.execute(BOOTSTRAP_DDL);
    }

    private Map<String, String> loadAppliedMigrations() {
        return clickhouseJdbcTemplate.query(
                SELECT_APPLIED,
                (rs, rowNum) -> Map.entry(
                        rs.getString("script_name"),
                        rs.getString("checksum")
                )
        ).stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    private List<Resource> discoverScripts() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        String pattern = "classpath:" + properties.scriptsLocation() + "/*.sql";
        Resource[] resources = resolver.getResources(pattern);

        return Arrays.stream(resources)
                .filter(Resource::isReadable)
                .sorted((a, b) -> {
                    String nameA = a.getFilename() != null ? a.getFilename() : "";
                    String nameB = b.getFilename() != null ? b.getFilename() : "";
                    return nameA.compareTo(nameB);
                })
                .toList();
    }

    private void verifyChecksum(String scriptName, String expectedChecksum, String actualChecksum) {
        if (!expectedChecksum.equals(actualChecksum)) {
            throw new IllegalStateException(
                    "ClickHouse migration checksum mismatch: script=%s, expected=%s, actual=%s. "
                            .formatted(scriptName, expectedChecksum, actualChecksum)
                            + "Already applied scripts must not be modified."
            );
        }
    }

    private void applyScript(String scriptName, String content, String checksum) {
        log.info("Applying ClickHouse migration: script={}", scriptName);

        try {
            clickhouseJdbcTemplate.execute(content);
        } catch (Exception e) {
            log.error("ClickHouse migration failed: script={}", scriptName, e);
            throw new IllegalStateException(
                    "ClickHouse migration failed: script=%s".formatted(scriptName), e
            );
        }

        int version = extractVersion(scriptName);
        clickhouseJdbcTemplate.update(INSERT_VERSION, version, scriptName, checksum);

        log.info("Applied ClickHouse migration: script={}, checksum={}", scriptName, checksum);
    }

    private static int extractVersion(String scriptName) {
        String prefix = scriptName.split("-", 2)[0];
        return Integer.parseInt(prefix);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
