package ru.vkim.datapulse.dwh.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.vkim.datapulse.common.RawFileArchiveService;
import ru.vkim.datapulse.dwh.model.RawFileEntity;
import ru.vkim.datapulse.dwh.repo.RawFileRepository;

@Service
@RequiredArgsConstructor
public class RawArchiveIndexerService {

    private final RawFileRepository rawRepo;

    public Mono<RawFileEntity> indexRaw(
            String marketplace,
            String shopId,
            String tokenHash,
            String logicalName,
            RawFileArchiveService.RawFileDescriptor f
    ) {
        RawFileEntity e = RawFileEntity.builder()
                .marketplace(marketplace)
                .shopId(shopId)
                .tokenHash(tokenHash)
                .logicalName(logicalName)
                .filePath(f.absolutePath())
                .fileSize(f.size())
                .checksum(f.checksum())
                .createdAt(f.createdAt())
                .build();
        return rawRepo.save(e);
    }
}
