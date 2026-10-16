package com.example.localledger.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingNavigation @Inject constructor() {
    private val route = MutableStateFlow<String?>(null)
    val routes: StateFlow<String?> = route

    fun request(route: String) {
        this.route.value = route
    }

    fun consume() {
        route.value = null
    }
}
