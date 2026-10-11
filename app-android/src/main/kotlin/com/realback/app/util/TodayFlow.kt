package com.realback.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * The current epoch day, re-emitted when the local date rolls over.
 *
 * View models must not capture "today" once at construction: this app is
 * routinely left open overnight, and a frozen date makes every read and write
 * land on yesterday — the dashboard shows yesterday's usage and a check-in
 * made at 00:05 is filed under the previous day.
 *
 * Polling the clock is deliberate. It costs one `LocalDate.now()` per
 * [pollMillis] while the screen is subscribed, and [distinctUntilChanged]
 * guarantees the downstream database flows only restart once per day, so there
 * is no need for an alarm at midnight.
 */
fun todayEpochDayFlow(pollMillis: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(LocalDate.now().toEpochDay())
        delay(pollMillis)
    }
}.distinctUntilChanged()

/**
 * Today's epoch day, evaluated at the moment of the call. Use this for writes;
 * a value cached at construction time can belong to yesterday by the time the
 * user taps the button.
 */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
