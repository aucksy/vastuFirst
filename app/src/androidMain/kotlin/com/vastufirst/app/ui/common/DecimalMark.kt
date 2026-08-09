package com.vastufirst.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DecimalFormatSymbols

/**
 * The decimal mark for the language the phone is actually showing this app in — asked of Android's
 * own locale data, never read off a table we would have to maintain and would eventually get wrong.
 *
 * ⭐ THIS IS NOT LOCALISATION AND IT SURVIVES THE ENGLISH-ONLY DECISION (CLAUDE.md §2e). The app's
 * words are English and always will be; this asks the phone one narrow question — which mark does
 * this device write between the whole part and the tenth — because that is the device's setting,
 * not ours. Without it, a phone set to a comma language would be shown "4.7" where its owner reads
 * "4,7", which looks like a typo in a number the whole product is judged on.
 *
 * ⭐ Why the mark and not the whole number. Android can format "4.7" end to end, and for some
 * locales it would also SHAPE THE DIGITS — a Marathi phone renders "४.७", a Bengali one "৪.৭",
 * because that is those locales' default numbering system. That is wrong here: every other number
 * VastuFirst prints (the ₹699 price, the degrees on Mark North, room sizes) is Western digits, and
 * the interface is English. A lone Bengali numeral in an English screen reads as a bug, not as a
 * courtesy. So we take the one thing that genuinely differs — the mark — and leave the digits alone.
 *
 * The lookup is the platform's, so a phone set to German gets "4,7" and Arabic "٤٫٧" for free even
 * though we ship neither and never will.
 */
@Composable
fun deviceDecimalMark(): Char {
    // The RESOLVED configuration locale (what the user is being shown), not Locale.getDefault() —
    // per-app language settings can make those two disagree.
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
}
