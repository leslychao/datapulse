package io.datapulse.etl.adapter.util;

import java.nio.file.Path;

public record TempFileWriteResult(
        Path path,
        String sha256,
        long byteSize
) {}
