package com.tana.spa.appupdate

import com.tana.spa.db.TestDatabase
import org.postgresql.util.PSQLException
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The constraints in `V7__app_update_config.sql` are the last line of defence behind the service,
 * so they are checked against real PostgreSQL.
 *
 * Every test here shares one database and one row, so none of them may assert the seeded *values*
 * — a test that ran earlier may already have overwritten them.
 */
class PostgresAppUpdateRepositoryTest {

    private companion object {
        const val CHECK_VIOLATION = "23514"
        val VERSION = Regex("""^\d+\.\d+\.\d+$""")
    }

    private val dataSource: DataSource = TestDatabase.dataSource
    private val repository = PostgresAppUpdateRepository(dataSource)

    private fun exec(sql: String) = dataSource.connection.use { it.createStatement().use { s -> s.execute(sql) } }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                rs.next()
                read(rs)
            }
        }
    }

    private fun rowCount(): Long = query("SELECT count(*) FROM app_update_config") { it.getLong(1) }

    private fun updatedBy(): String? = query("SELECT updated_by FROM app_update_config") { it.getString(1) }

    private fun updatedAt(): Timestamp = query("SELECT updated_at FROM app_update_config") { it.getTimestamp(1) }

    private fun assertViolation(state: String, block: () -> Unit) {
        val e = assertFailsWith<PSQLException> { block() }
        assertEquals(state, e.sqlState, "unexpected SQLSTATE: ${e.message}")
    }

    @Test
    fun theMigrationSeedsAReadableRow() {
        val config = assertNotNull(repository.config(), "V7 seeds one row")
        assertTrue(VERSION.matches(config.minimumVersion), "minimum '${config.minimumVersion}' is three segments")
        assertTrue(VERSION.matches(config.latestVersion), "latest '${config.latestVersion}' is three segments")
    }

    @Test
    fun savingEditsTheSameRowAndRecordsWhoDidIt() {
        repository.save(AppUpdateConfigRow("1.2.3", "1.4.0", forceUpdate = true), updatedBy = "ops-edit")

        assertEquals(AppUpdateConfigRow("1.2.3", "1.4.0", true), repository.config())
        assertEquals(1, rowCount(), "a save edits the row rather than adding one")
        assertEquals("ops-edit", updatedBy())
    }

    @Test
    fun aSecondRowIsImpossible() {
        assertViolation(CHECK_VIOLATION) {
            exec("INSERT INTO app_update_config (id, minimum_version, latest_version) VALUES ('other', '1.0.0', '2.0.0')")
        }
        assertEquals(1, rowCount())
    }

    @Test
    fun theSchemaRefusesAVersionThatIsNotThreeNumbers() {
        assertViolation(CHECK_VIOLATION) {
            exec("UPDATE app_update_config SET minimum_version = '1.0' WHERE id = 'global'")
        }
        assertViolation(CHECK_VIOLATION) {
            exec("UPDATE app_update_config SET latest_version = 'nightly' WHERE id = 'global'")
        }
    }

    @Test
    fun theUpdatedAtTriggerFiresOnEverySave() {
        repository.save(AppUpdateConfigRow("1.0.0", "1.0.1", forceUpdate = false), updatedBy = "ops-first")
        val before = updatedAt()

        repository.save(AppUpdateConfigRow("1.0.0", "1.0.2", forceUpdate = false), updatedBy = "ops-second")

        assertTrue(updatedAt() > before, "set_updated_at should have moved updated_at forward")
    }
}
