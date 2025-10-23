package io.datapulse.persistence;
    
    import io.datapulse.domain.model.Account;
    import io.datapulse.domain.model.Sale;
    import io.datapulse.domain.port.PersistencePort;
    import io.datapulse.persistence.entity.AccountEntity;
    import io.datapulse.persistence.mapper.AccountMapper;
    import io.datapulse.persistence.repo.AccountRepository;
    import jakarta.persistence.EntityManager;
    import jakarta.persistence.PersistenceContext;
    import java.sql.Date;
    import java.util.List;
    import lombok.RequiredArgsConstructor;
    import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
    import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
    import org.springframework.stereotype.Component;
    import org.springframework.transaction.annotation.Transactional;
    
    @Component
    @RequiredArgsConstructor
    public class PersistAdapter implements PersistencePort {
    
      private final AccountRepository accountRepository;
      private final AccountMapper accountMapper;
      private final NamedParameterJdbcTemplate jdbc;
    
      @PersistenceContext
      private EntityManager em;
    
      @Override
      @Transactional
      public Account saveAccount(Account account) {
        AccountEntity entity = accountMapper.toEntity(account);
        AccountEntity saved = accountRepository.save(entity);
        return accountMapper.toDomain(saved);
      }
    
      @Override
      public List<Account> findActiveAccounts() {
        return accountRepository.findByActiveTrue().stream().map(accountMapper::toDomain).toList();
      }
    
      @Override
      @Transactional
      public void upsertSalesBatch(List<Sale> salesBatch) {
        if (salesBatch == null || salesBatch.isEmpty()) return;
    
        String sql = """

            INSERT INTO sales_fact(account_id, sku, dt, quantity, revenue, cost, margin)

            VALUES (:accountId, :sku, :dt, :quantity, :revenue, :cost, :margin)

            ON CONFLICT (account_id, sku, dt) DO UPDATE

            SET quantity = EXCLUDED.quantity,

                revenue  = EXCLUDED.revenue,

                cost     = EXCLUDED.cost,

                margin   = EXCLUDED.margin

            """;
    
        var params = salesBatch.stream().map(s -> new MapSqlParameterSource()
            .addValue("accountId", s.getAccountId())
            .addValue("sku", s.getSku())
            .addValue("dt", Date.valueOf(s.getDate()))
            .addValue("quantity", s.getQuantity())
            .addValue("revenue", s.getRevenue())
            .addValue("cost", s.getCost())
            .addValue("margin", s.getMargin())
        ).toArray(MapSqlParameterSource[]::new);
    
        jdbc.batchUpdate(sql, params);
      }
    }
