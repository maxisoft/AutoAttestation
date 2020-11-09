package io.github.maxisoft.autoattestation.android

import android.annotation.SuppressLint
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuItemImpl

val MenuItem.showAsActionFlag: Int
    @SuppressLint("RestrictedApi")
    get() {
        this as MenuItemImpl
        return when {
            requiresActionButton() -> MenuItemImpl.SHOW_AS_ACTION_ALWAYS
            requestsActionButton() -> MenuItemImpl.SHOW_AS_ACTION_IF_ROOM
            showsTextAsAction() -> MenuItemImpl.SHOW_AS_ACTION_WITH_TEXT
            else -> MenuItemImpl.SHOW_AS_ACTION_NEVER
        }
    }