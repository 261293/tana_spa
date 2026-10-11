package com.tana.spa.appupdate

import javax.sql.DataSource

/** Schema: `V7__app_update_config.sql`, a single row pinned to [ROW_ID] by a check constraint. */
class PostgresAppUpdateRepository(private val dataSource: DataSource) : AppUpdateRepository {

    override fun config(): AppUpdateConfigRow? = dataSource.connection.use { c ->
        c.prepareStatement(
            "SELECT minimum_version, latest_version, force_update FROM app_update_config WHERE id = ?",
        ).use { ps ->
            ps.setString(1, ROW_ID)
            ps.executeQuery().use { rs ->
                if (!rs.next()) {
                    null
                } else {
                    AppUpdateConfigRow(
                        minimumVersion = rs.getString("minimum_version"),
                        latestVersion = rs.getString("latest_version"),
                        forceUpdate = rs.getBoolean("force_update"),
                    )
                }
            }
        }
    }

    override fun save(config: AppUpdateConfigRow, updatedBy: String?) {
        dataSource.connection.use { c ->
            c.prepareStatement(
                """INSERT INTO app_update_config (id, minimum_version, latest_version, force_update, updated_by)
                   VALUES (?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO UPDATE SET
                       minimum_version = excluded.minimum_version,
                       latest_version = excluded.latest_version,
                       force_update = excluded.force_update,
                       updated_by = excluded.updated_by""",
            ).use { ps ->
                ps.setString(1, ROW_ID)
                ps.setString(2, config.minimumVersion)
                ps.setString(3, config.latestVersion)
                ps.setBoolean(4, config.forceUpdate)
                ps.setString(5, updatedBy)
                ps.executeUpdate()
            }
        }
    }

    private companion object {
        const val ROW_ID = "global"
    }
}
