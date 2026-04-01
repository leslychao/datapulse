package io.datapulse.sellerops.persistence;

import io.datapulse.platform.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "saved_view")
@Getter
@Setter
public class SavedViewEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> filters;

    @Column(name = "sort_column", length = 60)
    private String sortColumn;

    @Column(name = "sort_direction", length = 4)
    private String sortDirection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visible_columns", columnDefinition = "jsonb", nullable = false)
    private List<String> visibleColumns;

    @Column(name = "group_by_sku", nullable = false)
    private boolean groupBySku;
}
