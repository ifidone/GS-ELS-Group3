package com.els.backend.database.calculations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SavedCalculationStore {

    // Reusable mapper for saved_calculations rows.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Double>> TIME_SERIES_TYPE = new TypeReference<>() {
    };
    private static final RowMapper<SavedCalculation> ROW_MAPPER = new RowMapper<>() {
        @Override
        public SavedCalculation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SavedCalculation(
                    rs.getLong("id"),
                    rs.getString("uid"),
                    rs.getString("name"),
                    rs.getString("ticker"),
                    rs.getDouble("initial_investment"),
                    rs.getDouble("years"),
                    rs.getDouble("beta"),
                    rs.getDouble("expected_return"),
                    rs.getDouble("future_value"),
                    parseTimeSeries(rs.getString("time_series")),
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
                select id, uid, name, ticker, initial_investment, years, beta, expected_return, future_value,
                       time_series::text as time_series, created_at, updated_at
                from saved_calculations
                where uid = ?
                order by created_at desc
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, uid);
    }

    public List<SavedCalculation> listByUidAndNameLike(String uid, String nameQuery) {
        String sql = """
                select id, uid, name, ticker, initial_investment, years, beta, expected_return, future_value,
                       time_series::text as time_series, created_at, updated_at
                from saved_calculations
                where uid = ?
                  and lower(name) like lower(?)
                order by created_at desc
                """;
        String pattern = "%" + nameQuery + "%";
        return jdbcTemplate.query(sql, ROW_MAPPER, uid, pattern);
    }

    // Insert a new saved calculation and return the created row.
    public SavedCalculation insert(String uid, SavedCalculationPayload payload) {
        String sql = """
                insert into saved_calculations (
                    uid,
                    name,
                    ticker,
                    initial_investment,
                    years,
                    beta,
                    expected_return,
                    future_value,
                    time_series,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                returning id, uid, name, ticker, initial_investment, years, beta, expected_return, future_value,
                          time_series::text as time_series, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(
                sql,
                ROW_MAPPER,
                uid,
                payload.name(),
                payload.ticker(),
                payload.initialInvestment(),
                payload.years(),
                payload.beta(),
                payload.expectedReturn(),
                payload.futureValue(),
                serializeTimeSeries(payload.timeSeries())
        );
    }

    // Update an existing saved calculation owned by the user.
    public Optional<SavedCalculation> update(String uid, long id, SavedCalculationPayload payload) {
        String sql = """
                update saved_calculations
                set
                    name = ?,
                    ticker = ?,
                    initial_investment = ?,
                    years = ?,
                    beta = ?,
                    expected_return = ?,
                    future_value = ?,
                    time_series = ?::jsonb,
                    updated_at = now()
                where uid = ? and id = ?
                returning id, uid, name, ticker, initial_investment, years, beta, expected_return, future_value,
                          time_series::text as time_series, created_at, updated_at
                """;
        List<SavedCalculation> results = jdbcTemplate.query(
                sql,
                ROW_MAPPER,
                payload.name(),
                payload.ticker(),
                payload.initialInvestment(),
                payload.years(),
                payload.beta(),
                payload.expectedReturn(),
                payload.futureValue(),
                serializeTimeSeries(payload.timeSeries()),
                uid,
                id
        );
        return results.stream().findFirst();
    }

    public Optional<SavedCalculation> getById(String uid, long id) {
        String sql = """
                select id, uid, name, ticker, initial_investment, years, beta, expected_return, future_value,
                       time_series::text as time_series, created_at, updated_at
                from saved_calculations
                where uid = ? and id = ?
                """;
        List<SavedCalculation> results = jdbcTemplate.query(sql, ROW_MAPPER, uid, id);
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
            String name,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            Map<String, Double> timeSeries,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SavedCalculationPayload(
            String name,
            String ticker,
            double initialInvestment,
            double years,
            double beta,
            double expectedReturn,
            double futureValue,
            Map<String, Double> timeSeries
    ) {
    }

    private static Map<String, Double> parseTimeSeries(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(value, TIME_SERIES_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static String serializeTimeSeries(Map<String, Double> timeSeries) {
        if (timeSeries == null || timeSeries.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(timeSeries);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
