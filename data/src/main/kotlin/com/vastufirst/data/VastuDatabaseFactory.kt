package com.vastufirst.data

import app.cash.sqldelight.db.SqlDriver
import com.vastufirst.data.db.VastuDatabase

/**
 * Wraps a platform-supplied [SqlDriver] into the [VastuDatabase].
 *
 * The driver is the ONLY platform seam: `:app` provides an `AndroidSqliteDriver` (Phase 2),
 * a future iOS build a `NativeSqliteDriver` (Phase 5), a JVM test a `JdbcSqliteDriver` — so
 * this module and everything above it stay Android-free (Impl PRD §3.1).
 *
 * Schema creation/migration is the driver's job (the Android driver runs it from the
 * `VastuDatabase.Schema` it is constructed with), so it is NOT repeated here.
 */
object VastuDatabaseFactory {
    fun create(driver: SqlDriver): VastuDatabase = VastuDatabase(driver)
}
