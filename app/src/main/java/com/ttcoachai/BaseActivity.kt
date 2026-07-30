/*
 * AI Coach for Table Tennis
 * Base Activity - Handles locale for all activities
 */

package com.ttcoachai

import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity for app screens.
 *
 * Locale is NOT applied here: `AppCompatDelegate.setApplicationLocales` (see [LocaleHelper])
 * applies the per-app language to every `AppCompatActivity` and re-creates them on change.
 * Overriding `attachBaseContext` here would pin a stale locale and fight that mechanism.
 */
abstract class BaseActivity : AppCompatActivity()
