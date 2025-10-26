package io.datapulse.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "review")
@Getter
@Setter
public class ReviewEntity extends LongBaseEntity {

  private Long accountId;
  private Long productId;
  private LocalDate date;
  private Integer rating;
  private String text;
  private String author;
  private Boolean replied;
}
