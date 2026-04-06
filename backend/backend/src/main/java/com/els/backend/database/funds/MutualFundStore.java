package com.els.backend.database.funds;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MutualFundStore {

    /**
     * When the database has no rows (fresh env, migrations not applied), the calculator UI still works
     * via frontend fallbacks while {@code /api/funds} was empty—breaking the AI portfolio builder which
     * requires at least four catalog names. Mirrors seed data from {@code V4__create_mutual_funds_table.sql}.
     */
    private static final List<MutualFund> CATALOG_FALLBACK = List.of(
            new MutualFund("VFIAX", "Vanguard 500 Index Fund Admiral Shares", "Large Blend"),
            new MutualFund("VTSAX", "Vanguard Total Stock Market Index Fund Admiral Shares", "Large Blend"),
            new MutualFund("FXAIX", "Fidelity 500 Index Fund", "Large Blend"),
            new MutualFund("SWPPX", "Schwab S&P 500 Index Fund", "Large Blend"),
            new MutualFund("VIGAX", "Vanguard Growth Index Fund Admiral Shares", "Large Growth"),
            new MutualFund("VIMAX", "Vanguard Mid-Cap Index Fund Admiral Shares", "Mid-Cap Blend"),
            new MutualFund("VSMAX", "Vanguard Small-Cap Index Fund Admiral Shares", "Small Blend"),
            new MutualFund("VTMGX", "Vanguard Developed Markets Index Fund Admiral Shares", "International"),
            new MutualFund("VEMAX", "Vanguard Emerging Markets Stock Index Fund Admiral Shares", "International"),
            new MutualFund("VBTLX", "Vanguard Total Bond Market Index Fund Admiral Shares", "Intermediate Bond")
    );

    private static final RowMapper<MutualFund> ROW_MAPPER = new RowMapper<>() {
        @Override
        public MutualFund mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MutualFund(
                    rs.getString("ticker"),
                    rs.getString("name"),
                    rs.getString("category")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public MutualFundStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MutualFund> listAll() {
        String sql = """
                select ticker, name, category
                from mutual_funds
                order by ticker asc
                """;
        List<MutualFund> fromDb = jdbcTemplate.query(sql, ROW_MAPPER);
        if (fromDb.isEmpty()) {
            return CATALOG_FALLBACK;
        }
        return fromDb;
    }

    public Optional<MutualFund> findByTicker(String ticker) {
        String sql = """
                select ticker, name, category
                from mutual_funds
                where ticker = ?
                """;
        List<MutualFund> results = jdbcTemplate.query(sql, ROW_MAPPER, ticker);
        return results.stream().findFirst();
    }

    public record MutualFund(
            String ticker,
            String name,
            String category
    ) {
    }
}
