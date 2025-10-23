package io.datapulse.persistence.entity;
    
    import io.datapulse.domain.MarketplaceType;
    import jakarta.persistence.*;
    import java.time.OffsetDateTime;
    import lombok.Getter;
    import lombok.Setter;
    
    @Entity
    @Table(name = "account")
    @Getter @Setter
    public class AccountEntity {
      @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
      private Long id;
    
      @Enumerated(EnumType.STRING)
      @Column(nullable = false)
      private MarketplaceType marketplace;
    
      @Column(nullable = false)
      private String name;
    
      @Column(name = "token_encrypted", nullable = false)
      private String tokenEncrypted;
    
      @Column(nullable = false)
      private boolean active;
    
      @Column(name = "created_at", nullable = false)
      private OffsetDateTime createdAt;
    }
