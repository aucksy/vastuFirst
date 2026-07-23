package com.vastufirst.app.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.vastufirst.data.db.VastuDatabase

/**
 * The single Android platform seam for persistence. `AndroidSqliteDriver` creates and migrates
 * the schema from [VastuDatabase.Schema], so the pure `data` module never touches Android.
 */
fun createAndroidSqlDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(VastuDatabase.Schema, context, "vastufirst.db")
