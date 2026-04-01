package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.CreateSavedViewRequest;
import io.datapulse.sellerops.api.SavedViewSummaryResponse;
import io.datapulse.sellerops.api.UpdateSavedViewRequest;
import io.datapulse.sellerops.persistence.SavedViewEntity;
import io.datapulse.sellerops.persistence.SavedViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedViewServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long USER_ID = 10L;
  private static final long VIEW_ID = 100L;

  @Mock
  private SavedViewRepository repository;

  @InjectMocks
  private SavedViewService service;

  @Nested
  @DisplayName("createView")
  class CreateView {

    @Test
    void should_create_view_when_name_unique() {
      var request = new CreateSavedViewRequest(
          "My View", false, Map.of(), "sku", "ASC",
          List.of("sku", "name"), false);

      when(repository.existsByWorkspaceIdAndUserIdAndName(WORKSPACE_ID, USER_ID, "My View"))
          .thenReturn(false);
      when(repository.save(any())).thenAnswer(inv -> {
        SavedViewEntity e = inv.getArgument(0);
        e.setId(VIEW_ID);
        return e;
      });

      SavedViewSummaryResponse result = service.createView(WORKSPACE_ID, USER_ID, request);

      assertThat(result.name()).isEqualTo("My View");
      verify(repository).save(any(SavedViewEntity.class));
    }

    @Test
    void should_throw_when_name_duplicate() {
      var request = new CreateSavedViewRequest(
          "Existing", false, Map.of(), null, null, List.of(), false);

      when(repository.existsByWorkspaceIdAndUserIdAndName(WORKSPACE_ID, USER_ID, "Existing"))
          .thenReturn(true);

      assertThatThrownBy(() -> service.createView(WORKSPACE_ID, USER_ID, request))
          .isInstanceOf(BadRequestException.class);

      verify(repository, never()).save(any());
    }

    @Test
    void should_clear_other_defaults_when_creating_default_view() {
      var request = new CreateSavedViewRequest(
          "New Default", true, Map.of(), null, null, List.of(), false);

      var existingDefault = buildView(VIEW_ID + 1, "Old Default", true, false);

      when(repository.existsByWorkspaceIdAndUserIdAndName(WORKSPACE_ID, USER_ID, "New Default"))
          .thenReturn(false);
      when(repository.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(WORKSPACE_ID, USER_ID))
          .thenReturn(List.of(existingDefault));
      when(repository.save(any())).thenAnswer(inv -> {
        SavedViewEntity e = inv.getArgument(0);
        if (e.getId() == null) e.setId(VIEW_ID);
        return e;
      });

      service.createView(WORKSPACE_ID, USER_ID, request);

      assertThat(existingDefault.isDefault()).isFalse();
    }
  }

  @Nested
  @DisplayName("updateView")
  class UpdateView {

    @Test
    void should_update_view_when_owner_and_not_system() {
      var entity = buildView(VIEW_ID, "Old Name", false, false);
      entity.setUserId(USER_ID);
      var request = new UpdateSavedViewRequest(
          "New Name", false, Map.of(), "price", "DESC", List.of("price"), true);

      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));
      when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      service.updateView(WORKSPACE_ID, USER_ID, VIEW_ID, request);

      assertThat(entity.getName()).isEqualTo("New Name");
      assertThat(entity.isGroupBySku()).isTrue();
    }

    @Test
    void should_throw_when_system_view() {
      var entity = buildView(VIEW_ID, "System", false, true);
      entity.setUserId(USER_ID);
      var request = new UpdateSavedViewRequest(
          "Hack", false, Map.of(), null, null, List.of(), false);

      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() ->
          service.updateView(WORKSPACE_ID, USER_ID, VIEW_ID, request))
          .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_throw_when_different_user() {
      var entity = buildView(VIEW_ID, "Other User View", false, false);
      entity.setUserId(999L);
      var request = new UpdateSavedViewRequest(
          "Hack", false, Map.of(), null, null, List.of(), false);

      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() ->
          service.updateView(WORKSPACE_ID, USER_ID, VIEW_ID, request))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("deleteView")
  class DeleteView {

    @Test
    void should_delete_when_owner_and_not_system() {
      var entity = buildView(VIEW_ID, "My View", false, false);
      entity.setUserId(USER_ID);

      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      service.deleteView(WORKSPACE_ID, USER_ID, VIEW_ID);

      verify(repository).delete(entity);
    }

    @Test
    void should_throw_when_system_view() {
      var entity = buildView(VIEW_ID, "System", false, true);
      entity.setUserId(USER_ID);

      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.of(entity));

      assertThatThrownBy(() -> service.deleteView(WORKSPACE_ID, USER_ID, VIEW_ID))
          .isInstanceOf(BadRequestException.class);

      verify(repository, never()).delete(any());
    }

    @Test
    void should_throw_when_not_found() {
      when(repository.findByIdAndWorkspaceId(VIEW_ID, WORKSPACE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deleteView(WORKSPACE_ID, USER_ID, VIEW_ID))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listViews")
  class ListViews {

    @Test
    void should_return_summaries() {
      var entity = buildView(VIEW_ID, "Test", false, false);
      when(repository.findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(WORKSPACE_ID, USER_ID))
          .thenReturn(List.of(entity));

      List<SavedViewSummaryResponse> result = service.listViews(WORKSPACE_ID, USER_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).name()).isEqualTo("Test");
    }
  }

  private SavedViewEntity buildView(long id, String name, boolean isDefault, boolean isSystem) {
    var entity = new SavedViewEntity();
    entity.setId(id);
    entity.setWorkspaceId(WORKSPACE_ID);
    entity.setUserId(USER_ID);
    entity.setName(name);
    entity.setDefault(isDefault);
    entity.setSystem(isSystem);
    entity.setFilters(Map.of());
    entity.setVisibleColumns(List.of());
    return entity;
  }
}
