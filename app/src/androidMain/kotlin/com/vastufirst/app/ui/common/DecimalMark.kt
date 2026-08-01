package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DecimalFormatSymbols

/**
 * The decimal mark for the language the phone is actually showing this app in — asked of Android's
 * own locale data, never read off a table we would have to maintain and would eventually get wrong.
 *
 * ⭐ Why the mark and not the whole number. Android can format "4.7" end to end, and for two of our
 * six languages it would also SHAPE THE DIGITS: Marathi renders it "४.७" and Bengali "৪.৭", because
 * that is those languages' default numbering system. That is correct in a fully translated app and
 * wrong in this one — every other number VastuFirst prints (the ₹699 price, the degrees on Mark
 * North, room sizes) is Western digits, and the interface is still English until the Phase 4
 * translations land. A lone Bengali numeral in an English screen reads as a bug, not as a
 * courtesy. So we take the one thing that genuinely differs between our six languages — the mark —
 * and leave the digits alone. When the translations ship, the numbering system becomes a
 * deliberate choice made alongside them, in one place: here.
 *
 * The lookup is the platform's, so a phone set to German gets "4,7" and Arabic "٤٫٧" for free even
 * though we ship neither.
 */
@Composable
fun deviceDecimalMark(): Char {
    // The RESOLVED configuration locale (what the user is being shown), not Locale.getDefault() —
    // per-app language settings can make those two disagree.
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
}
