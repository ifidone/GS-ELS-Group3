from typing import Any

from fastmcp import FastMCP

from db.connection import get_connection, get_dict_cursor, normalize_row


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def saved_calculations_list(uid: str) -> list[dict[str, Any]]:
        """List saved calculations for a user."""
        if not uid or not uid.strip():
            raise ValueError("uid is required")

        user_sql = "select 1 from users where uid = %s"
        sql = """
            select id, uid, ticker, initial_investment, years, beta, expected_return, future_value, created_at, updated_at
            from saved_calculations
            where uid = %s
            order by created_at desc
        """
        with get_connection() as conn:
            with get_dict_cursor(conn) as cursor:
                cursor.execute(user_sql, (uid.strip(),))
                if cursor.fetchone() is None:
                    raise ValueError("user not found")
                cursor.execute(sql, (uid.strip(),))
                rows = cursor.fetchall()
                return [normalize_row(row) for row in rows]
