package com.els.backend.database.calculations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SavedCalculationStore {

    // Reusable mapper for saved_calculations rows.
    private static final RowMapper<SavedCalculation> ROW_MAPPER = new RowMapper<>() {
        @Override
        public SavedCalculation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SavedCalculation(
                    rs.getLong("id"),
                    rs.getString("uid"),
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

    public SavedCalculationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Fetch saved calculations for a user, newest first.
    public List<SavedCalculation> listByUid(String uid) {
        String sql = """
                select id, uid, ticker, initial_investment, years, beta, expected_return, future_value, created_at, updated_at
                from saved_calculations
                where uid = ?
                order by created_at desc
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, uid);
    }

    // Insert a new saved calculation and return the created row.
    public SavedCalculation insert(String uid, SavedCalculationPayload payload) {
        String sql = """
                insert into saved_calculations (
                    uid,
                    ticker,
                    initial_investment,
                    years,
                    beta,
                    expected_return,
                    future_value,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, now(), now())
                returning id, uid, ticker, initial_investment, years, beta, expected_return, future_value, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(
                sql,
                ROW_MAPPER,
                uid,
                payload.ticker(),
                payload.initialInvestment(),
                payload.years(),
                payload.beta(),
                payload.expectedReturn(),
                payload.futureValue()
        );
    }

    // Update an existing saved calculation owned by the user.
    public Optional<SavedCalculation> update(String uid, long id, SavedCalculationPayload payload) {
        String sql = """
                update saved_calculations
                set
                    ticker = ?,
                    initial_investment = ?,
                    years = ?,
                    beta = ?,
                    expected_return = ?,
                    future_value = ?,
                    updated_at = now()
                where uid = ? and id = ?
                returning id, uid, ticker, initial_investment, years, beta, expected_return, future_value, created_at, updated_at
                """;
        List<SavedCalculation> results = jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                payload.ticker(),
                payload.initialInvestment(),
                payload.years(),
                payload.beta(),
                payload.expectedReturn(),
                payload.futureValue(),
                uid,
                id
        );
        return results.stream().findFirst();
    }

    // Delete a saved calculation if it belongs to the user.
    public boolean delete(String uid, long id) {
        String sql = "delete from saved_calculations where uid = ? and id = ?";
        return jdbcTemplate.update(sql, uid, id) > 0;
    }

    public record SavedCalculation(
            long id,
            String uid,
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

    public record SavedCalculationPayload(
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue
    ) {
    }
}
