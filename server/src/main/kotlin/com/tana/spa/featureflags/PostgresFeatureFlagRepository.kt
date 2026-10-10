package com.tana.spa.featureflags

import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/** Schema: `V6__feature_flags.sql`. */
class PostgresFeatureFlagRepository(private val dataSource: DataSource) : FeatureFlagRepository {

    override fun globals(): Map<String, Boolean> = dataSource.connection.use { c ->
        c.prepareStatement("SELECT key, enabled FROM feature_flags").use { ps ->
            ps.executeQuery().use { it.toEnabledByKey("key") }
        }
    }

    override fun overridesFor(userId: String): Map<String, Boolean> {
        val user = uuid(userId) ?: return emptyMap()
        return dataSource.connection.use { c ->
            c.prepareStatement("SELECT flag_key, enabled FROM feature_flag_overrides WHERE user_id = ?").use { ps ->
                ps.setObject(1, user)
                ps.executeQuery().use { it.toEnabledByKey("flag_key") }
            }
        }
    }

    override fun allOverrides(): List<FeatureFlagOverrideRow> = dataSource.connection.use { c ->
        c.prepareStatement(
            "SELECT flag_key, user_id, enabled, note FROM feature_flag_overrides ORDER BY flag_key, updated_at",
        ).use { ps ->
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toOverride()) }
            }
        }
    }

    override fun setGlobal(key: String, enabled: Boolean, description: String?, updatedBy: String?) {
        dataSource.connection.use { c ->
            c.prepareStatement(
                """INSERT INTO feature_flags (key, enabled, description, updated_by)
                   VALUES (?, ?, ?, ?)
                   ON CONFLICT (key) DO UPDATE SET
                       enabled = excluded.enabled,
                       description = COALESCE(excluded.description, feature_flags.description),
                       updated_by = excluded.updated_by""",
            ).use { ps ->
                ps.setString(1, key)
                ps.setBoolean(2, enabled)
                ps.setString(3, description)
                ps.setString(4, updatedBy)
                ps.executeUpdate()
            }
        }
    }

    override fun clearGlobal(key: String): Boolean = dataSource.connection.use { c ->
        c.prepareStatement("DELETE FROM feature_flags WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeUpdate() > 0
        }
    }

    override fun setOverride(key: String, userId: String, enabled: Boolean, note: String?, updatedBy: String?) {
        val user = requireNotNull(uuid(userId)) { "userId is not a uuid" }
        dataSource.connection.use { c ->
            c.prepareStatement(
                """INSERT INTO feature_flag_overrides (flag_key, user_id, enabled, note, updated_by)
                   VALUES (?, ?, ?, ?, ?)
                   ON CONFLICT (flag_key, user_id) DO UPDATE SET
                       enabled = excluded.enabled,
                       note = COALESCE(excluded.note, feature_flag_overrides.note),
                       updated_by = excluded.updated_by""",
            ).use { ps ->
                ps.setString(1, key)
                ps.setObject(2, user)
                ps.setBoolean(3, enabled)
                ps.setString(4, note)
                ps.setString(5, updatedBy)
                ps.executeUpdate()
            }
        }
    }

    override fun clearOverride(key: String, userId: String): Boolean {
        val user = uuid(userId) ?: return false
        return dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM feature_flag_overrides WHERE flag_key = ? AND user_id = ?").use { ps ->
                ps.setString(1, key)
                ps.setObject(2, user)
                ps.executeUpdate() > 0
            }
        }
    }

    private fun ResultSet.toEnabledByKey(keyColumn: String): Map<String, Boolean> {
        val result = LinkedHashMap<String, Boolean>()
        while (next()) result[getString(keyColumn)] = getBoolean("enabled")
        return result
    }

    private fun ResultSet.toOverride() = FeatureFlagOverrideRow(
        key = getString("flag_key"),
        userId = getObject("user_id", UUID::class.java).toString(),
        enabled = getBoolean("enabled"),
        note = getString("note"),
    )

    // Ids come from a JWT subject, so a malformed one is a rejected token, not a query.
    private fun uuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
