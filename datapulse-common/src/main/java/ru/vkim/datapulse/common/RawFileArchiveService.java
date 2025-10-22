package ru.vkim.datapulse.common;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(FileStorageProperties.class)
public class RawFileArchiveService {

    private final FileStorageProperties props;
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyyMMdd");

    public File resolveTokenDir(String marketplace, String tokenHash) {
        String d = LocalDate.now().format(D);
        File dir = new File(props.getBaseDir(), marketplace + "/" + d + "/" + tokenHash);
        dir.mkdirs();
        return dir;
    }

    public RawFileDescriptor writeJson(File dir, String logicalName, String json) throws IOException {
        String safe = logicalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String checksum = sha256(json).substring(0, 16);
        String filename = safe + "_" + checksum + ".json";
        File f = new File(dir, filename);
        FileUtils.writeStringToFile(f, json, StandardCharsets.UTF_8);
        return new RawFileDescriptor(f.getAbsolutePath(), filename, checksum, f.length(), OffsetDateTime.now());
    }

    private static String sha256(String s) {
        try {
            var d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "sha256_err";
        }
    }

    public record RawFileDescriptor(
            String absolutePath,
            String filename,
            String checksum,
            long size,
            OffsetDateTime createdAt
    ) {}
}
