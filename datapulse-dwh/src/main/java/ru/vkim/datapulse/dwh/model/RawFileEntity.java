package ru.vkim.datapulse.dwh.model;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Value
@Builder
@Table("ops.raw_files")
public class RawFileEntity {
    @Id
    Long id;

    @Column("marketplace")
    String marketplace;

    @Column("shop_id")
    String shopId;

    @Column("token_hash")
    String tokenHash;

    @Column("logical_name")
    String logicalName;

    @Column("file_path")
    String filePath;

    @Column("file_size")
    Long fileSize;

    @Column("checksum")
    String checksum;

    @Column("created_at")
    OffsetDateTime createdAt;
}
