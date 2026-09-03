package com.example.todolist.ui.common

import androidx.navigation.NavOptions
import com.example.todolist.R

/**
 * Fix #2: shared screen transition animations (content loading-in).
 * Used by every ID-based navigation so screens animate instead of hard-swapping.
 */
fun navAnimOptions(builder: NavOptions.Builder.() -> Unit = {}): NavOptions {
    return NavOptions.Builder()
        .setEnterAnim(R.anim.fragment_enter)
        .setExitAnim(R.anim.fragment_exit)
        .setPopEnterAnim(R.anim.fragment_pop_enter)
        .setPopExitAnim(R.anim.fragment_pop_exit)
        .apply(builder)
        .build()
}
