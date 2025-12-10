package io.datapulse.marketplaces.dto.raw.category;

import lombok.Data;

@Data
public class WbSubjectListRaw {

  private Long subjectID;
  private Long parentID;
  private String subjectName;
  private String parentName;
}
