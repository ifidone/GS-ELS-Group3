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
        return jdbcTemplate.query(sql, ROW_MAPPER);
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
