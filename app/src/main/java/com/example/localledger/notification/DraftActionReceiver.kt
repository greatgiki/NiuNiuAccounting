package com.example.localledger.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.localledger.data.repository.DraftConfirmResult
import com.example.localledger.data.repository.DraftRepository
import com.example.localledger.di.ApplicationScope
import com.example.localledger.ui.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DraftActionReceiver : BroadcastReceiver() {
    @Inject lateinit var draftRepository: DraftRepository
    @Inject lateinit var notifier: DraftNotifier
    @Inject lateinit var pendingNavigation: PendingNavigation
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val draftId = intent.getStringExtra(EXTRA_DRAFT_ID) ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        appScope.launch {
            try {
                when (action) {
                    ACTION_DISMISS -> draftRepository.dismiss(draftId)
                    ACTION_CONFIRM -> {
                        when (draftRepository.confirm(draftId)) {
                            DraftConfirmResult.NeedsAmount -> {
                                pendingNavigation.request(Routes.DRAFTS)
                                notifier.showNeedAmount(draftId)
                                return@launch
                            }
                            is DraftConfirmResult.MissingAssignment -> {
                                pendingNavigation.request(Routes.DRAFTS)
                                notifier.showNeedAssignment(draftId)
                                return@launch
                            }
                            else -> Unit
                        }
                    }
                }
                notifier.cancel(draftId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.example.localledger.action.CONFIRM_DRAFT"
        const val ACTION_DISMISS = "com.example.localledger.action.DISMISS_DRAFT"
        const val EXTRA_DRAFT_ID = "draft_id"
    }
}
