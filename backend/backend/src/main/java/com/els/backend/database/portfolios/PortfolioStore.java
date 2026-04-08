package com.els.backend.database.portfolios;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PortfolioStore {

    // Store methods always scope lookups by uid to enforce ownership at the DB layer.
    private static final RowMapper<Portfolio> PORTFOLIO_ROW_MAPPER = new RowMapper<>() {
        @Override
        public Portfolio mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Portfolio(
                    rs.getLong("id"),
                    rs.getString("uid"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    };

    private static final RowMapper<PortfolioSummary> SUMMARY_ROW_MAPPER = new RowMapper<>() {
        @Override
        public PortfolioSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PortfolioSummary(
                    rs.getLong("id"),
                    rs.getString("uid"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("calculation_count"),
                    rs.getDouble("total_principal"),
                    rs.getDouble("total_future_value"),
                    rs.getDouble("avg_beta")
            );
        }
    };

    private static final RowMapper<LinkedCalculation> LINKED_ROW_MAPPER = new RowMapper<>() {
        @Override
        public LinkedCalculation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new LinkedCalculation(
                    rs.getLong("id"),
                    rs.getString("ticker"),
                    rs.getDouble("initial_investment"),
                    rs.getDouble("years"),
                    rs.getDouble("beta"),
                    rs.getDouble("expected_return"),
                    rs.getDouble("future_value"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public PortfolioStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PortfolioSummary> listSummaries(String uid, int limit, int offset, String orderBy) {
        String sql = """
                select
                    p.id,
                    p.uid,
                    p.name,
                    p.description,
                    p.created_at,
                    p.updated_at,
                    coalesce(count(sc.id), 0) as calculation_count,
                    coalesce(sum(sc.initial_investment), 0) as total_principal,
                    coalesce(sum(sc.future_value), 0) as total_future_value,
                    coalesce(sum(sc.beta * sc.initial_investment) / nullif(sum(sc.initial_investment), 0), 0) as avg_beta
                from portfolios p
                left join portfolio_items pi on pi.portfolio_id = p.id
                left join saved_calculations sc on sc.id = pi.calculation_id
                where p.uid = ?
                group by p.id
                order by %s
                limit ? offset ?
                """.formatted(orderBy);
        return jdbcTemplate.query(sql, SUMMARY_ROW_MAPPER, uid, limit, offset);
    }

    public List<PortfolioSummary> listAllSummaries(String uid) {
        String sql = """
                select
                    p.id,
                    p.uid,
                    p.name,
                    p.description,
                    p.created_at,
                    p.updated_at,
                    coalesce(count(sc.id), 0) as calculation_count,
                    coalesce(sum(sc.initial_investment), 0) as total_principal,
                    coalesce(sum(sc.future_value), 0) as total_future_value,
                    coalesce(sum(sc.beta * sc.initial_investment) / nullif(sum(sc.initial_investment), 0), 0) as avg_beta
                from portfolios p
                left join portfolio_items pi on pi.portfolio_id = p.id
                left join saved_calculations sc on sc.id = pi.calculation_id
                where p.uid = ?
                group by p.id
                order by p.created_at desc
                """;
        return jdbcTemplate.query(sql, SUMMARY_ROW_MAPPER, uid);
    }

    public Portfolio create(String uid, String name, String description) {
        String sql = """
                insert into portfolios (
                    uid,
                    name,
                    description,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, now(), now())
                returning id, uid, name, description, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(sql, PORTFOLIO_ROW_MAPPER, uid, name, description);
    }

    public Optional<Portfolio> getById(String uid, long id) {
        String sql = """
                select id, uid, name, description, created_at, updated_at
                from portfolios
                where uid = ? and id = ?
                """;
        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, uid, id);
        return results.stream().findFirst();
    }

    public Optional<Portfolio> getByName(String uid, String name) {
        String sql = """
                select id, uid, name, description, created_at, updated_at
                from portfolios
                where uid = ? and lower(name) = lower(?)
                """;
        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, uid, name);
        return results.stream().findFirst();
    }

    public Optional<Long> getIdByName(String uid, String name) {
        String sql = """
                select id
                from portfolios
                where uid = ? and lower(name) = lower(?)
                """;
        List<Long> results = jdbcTemplate.queryForList(sql, Long.class, uid, name);
        return results.stream().findFirst();
    }

    public Optional<Portfolio> update(String uid, long id, String name, String description) {
        String sql = """
                update portfolios
                set
                    name = ?,
                    description = ?,
                    updated_at = now()
                where uid = ? and id = ?
                returning id, uid, name, description, created_at, updated_at
                """;
        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, name, description, uid, id);
        return results.stream().findFirst();
    }

    public Optional<Portfolio> updateByName(String uid, String currentName, String name, String description) {
        String sql = """
                update portfolios
                set
                    name = ?,
                    description = ?,
                    updated_at = now()
                where uid = ? and lower(name) = lower(?)
                returning id, uid, name, description, created_at, updated_at
                """;
        List<Portfolio> results = jdbcTemplate.query(sql, PORTFOLIO_ROW_MAPPER, name, description, uid, currentName);
        return results.stream().findFirst();
    }

    public boolean delete(String uid, long id) {
        String sql = "delete from portfolios where uid = ? and id = ?";
        return jdbcTemplate.update(sql, uid, id) > 0;
    }

    public boolean deleteByName(String uid, String name) {
        String sql = "delete from portfolios where uid = ? and lower(name) = lower(?)";
        return jdbcTemplate.update(sql, uid, name) > 0;
    }

    public boolean portfolioExists(String uid, long id) {
        String sql = "select exists (select 1 from portfolios where uid = ? and id = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, uid, id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean portfolioExistsByName(String uid, String name) {
        String sql = "select exists (select 1 from portfolios where uid = ? and lower(name) = lower(?))";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, uid, name);
        return Boolean.TRUE.equals(exists);
    }

    public boolean portfolioNameExists(String uid, String name) {
        String sql = "select exists (select 1 from portfolios where uid = ? and lower(name) = lower(?))";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, uid, name);
        return Boolean.TRUE.equals(exists);
    }

    public boolean portfolioNameExistsExcludingId(String uid, String name, long id) {
        String sql = "select exists (select 1 from portfolios where uid = ? and lower(name) = lower(?) and id <> ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, uid, name, id);
        return Boolean.TRUE.equals(exists);
    }

    public boolean calculationExistsForUid(String uid, long calculationId) {
        String sql = "select exists (select 1 from saved_calculations where uid = ? and id = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, uid, calculationId);
        return Boolean.TRUE.equals(exists);
    }

    public List<LinkedCalculation> listLinkedCalculations(String uid, long portfolioId) {
        String sql = """
                select
                    sc.id,
                    sc.ticker,
                    sc.initial_investment,
                    sc.years,
                    sc.beta,
                    sc.expected_return,
                    sc.future_value,
                    sc.created_at,
                    sc.updated_at
                from portfolio_items pi
                join portfolios p on p.id = pi.portfolio_id
                join saved_calculations sc on sc.id = pi.calculation_id
                where p.uid = ? and p.id = ?
                order by sc.created_at desc
                """;
        return jdbcTemplate.query(sql, LINKED_ROW_MAPPER, uid, portfolioId);
    }

    public boolean addCalculation(String uid, long portfolioId, long calculationId) {
        String sql = """
                insert into portfolio_items (portfolio_id, calculation_id, created_at)
                select p.id, sc.id, now()
                from portfolios p
                join saved_calculations sc on sc.id = ?
                where p.id = ? and p.uid = ? and sc.uid = ?
                on conflict do nothing
                """;
        return jdbcTemplate.update(sql, calculationId, portfolioId, uid, uid) > 0;
    }

    public boolean removeCalculation(String uid, long portfolioId, long calculationId) {
        String sql = """
                delete from portfolio_items pi
                using portfolios p, saved_calculations sc
                where pi.portfolio_id = p.id
                  and pi.calculation_id = sc.id
                  and p.id = ?
                  and p.uid = ?
                  and sc.id = ?
                  and sc.uid = ?
                """;
        return jdbcTemplate.update(sql, portfolioId, uid, calculationId, uid) > 0;
    }

    public List<LinkedCalculation> listAvailableCalculations(String uid, long portfolioId) {
        String sql = """
                select
                    sc.id,
                    sc.ticker,
                    sc.initial_investment,
                    sc.years,
                    sc.beta,
                    sc.expected_return,
                    sc.future_value,
                    sc.created_at,
                    sc.updated_at
                from saved_calculations sc
                where sc.uid = ?
                  and not exists (
                      select 1 from portfolio_items pi
                      where pi.portfolio_id = ? and pi.calculation_id = sc.id
                  )
                order by sc.created_at desc
                """;
        return jdbcTemplate.query(sql, LINKED_ROW_MAPPER, uid, portfolioId);
    }

    public record Portfolio(
            long id,
            String uid,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PortfolioSummary(
            long id,
            String uid,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt,
            long calculationCount,
            double totalPrincipal,
            double totalFutureValue,
            double avgBeta
    ) {
    }

    public record LinkedCalculation(
            long id,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
