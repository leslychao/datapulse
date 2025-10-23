package io.datapulse.persistence.entity;
    
    import jakarta.persistence.*;
    import java.time.LocalDate;
    import lombok.Getter;
    import lombok.Setter;
    
    @Entity
    @Table(name = "sales_fact")
    @Getter @Setter
    public class SaleEntity {
      @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;
    
      @Column(name = "account_id", nullable = false)
      private Long accountId;
    
      @Column(nullable = false)
      private String sku;
    
      @Column(name = "dt", nullable = false)
      private LocalDate date;
    
      @Column(nullable = false)
      private int quantity;
    
      @Column(nullable = false)
      private double revenue;
    
      @Column(nullable = false)
      private double cost;
    
      @Column(nullable = false)
      private double margin;
    }
