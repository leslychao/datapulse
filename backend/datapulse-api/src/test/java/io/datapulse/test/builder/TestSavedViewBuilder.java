package io.datapulse.test.builder;

import io.datapulse.sellerops.persistence.SavedViewEntity;

import java.util.List;
import java.util.Map;

public class TestSavedViewBuilder {

  private Long workspaceId;
  private Long userId = 1L;
  private String name = "Test View";
  private boolean isDefault = false;
  private boolean isSystem = false;
  private Map<String, Object> filters = Map.of();
  private String sortColumn = "name";
  private String sortDirection = "ASC";
  private List<String> visibleColumns = List.of("name", "price", "stock");
  private boolean groupBySku = false;

  public static TestSavedViewBuilder aSavedView() {
    return new TestSavedViewBuilder();
  }

  public TestSavedViewBuilder withWorkspaceId(Long workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public TestSavedViewBuilder withUserId(Long userId) {
    this.userId = userId;
    return this;
  }

  public TestSavedViewBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public TestSavedViewBuilder withIsDefault(boolean isDefault) {
    this.isDefault = isDefault;
    return this;
  }

  public SavedViewEntity build() {
    var entity = new SavedViewEntity();
    entity.setWorkspaceId(workspaceId);
    entity.setUserId(userId);
    entity.setName(name);
    entity.setDefault(isDefault);
    entity.setSystem(isSystem);
    entity.setFilters(filters);
    entity.setSortColumn(sortColumn);
    entity.setSortDirection(sortDirection);
    entity.setVisibleColumns(visibleColumns);
    entity.setGroupBySku(groupBySku);
    return entity;
  }
}
