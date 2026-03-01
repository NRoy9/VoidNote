package com.greenicephoenix.voidnote.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenicephoenix.voidnote.data.local.PreferencesManager
import com.greenicephoenix.voidnote.data.security.NoteEncryptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SplashViewModel — determines where to navigate after the splash animation.
 *
 * DECISION TREE (evaluated once at app start):
 *
 *   Onboarding complete?
 *   ├─ NO  → Navigate to Onboarding
 *   └─ YES
 *       Vault set up?
 *       ├─ NO  → Navigate to Onboarding (so user reaches VaultSetup at the end)
 *       └─ YES
 *           Try to load session key from wrapped key in DataStore
 *           ├─ SUCCESS → key is in memory, navigate to NotesList
 *           └─ FAIL (Keystore key gone — reinstall / factory reset)
 *               Navigate to VaultUnlock (ask for vault password to re-derive)
 *
 * WHY ROUTE TO ONBOARDING IF VAULT NOT SET UP (EVEN IF ONBOARDING WAS DONE)?
 * This handles the edge case where:
 * - User completed onboarding on an old build that didn't have vault setup
 * - App updated and now requires vault
 * - We can't just drop them into vault setup cold — show onboarding again
 *   so the vault setup screen follows naturally at the end.
 * It also handles a crash mid-setup: if onboarding is done but vault is not,
 * something went wrong — re-doing onboarding is safer than a blank unlock screen.
 *
 * WHAT ABOUT BIOMETRIC LOCK?
 * Biometric lock (the gate in MainActivity) is separate from vault unlock.
 * SplashViewModel only deals with getting the encryption key into memory.
 * MainActivity's biometric gate decides if the user can SEE the app after that.
 * These are two independent concerns:
 * - Vault key   → is the encryption/decryption machinery ready?
 * - Biometric   → is this person allowed to open the app right now?
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val encryption: NoteEncryptionManager
) : ViewModel() {

    sealed class Destination {
        object Loading     : Destination()      // Still checking, don't navigate yet
        object Onboarding  : Destination()      // Show onboarding (+ vault setup at end)
        object VaultUnlock : Destination()      // Keystore key gone, need password
        object NotesList   : Destination()      // Everything ready, show the app
    }

    private val _destination = MutableStateFlow<Destination>(Destination.Loading)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    init {
        checkStartDestination()
    }

    private fun checkStartDestination() {
        viewModelScope.launch {
            val onboardingDone = preferencesManager.onboardingCompleteFlow.first()
            val vaultDone      = preferencesManager.vaultSetupCompleteFlow.first()

            when {
                // Case 1: Onboarding not complete, or vault not set up yet
                // Route through onboarding → VaultSetup is at the end of that flow
                !onboardingDone || !vaultDone -> {
                    _destination.value = Destination.Onboarding
                }

                // Case 2: Both complete — try to load key from Keystore-wrapped value
                else -> {
                    val wrappedKey = preferencesManager.vaultWrappedKeyFlow.first()
                    val loaded = if (wrappedKey.isNotEmpty()) {
                        encryption.tryLoadFromWrapped(wrappedKey)
                    } else {
                        false
                    }

                    _destination.value = if (loaded) {
                        // Key loaded into memory — encryption ready, open the app
                        Destination.NotesList
                    } else {
                        // Keystore key gone (reinstall / factory reset)
                        // User must enter vault password to re-derive the key
                        Destination.VaultUnlock
                    }
                }
            }
        }
    }
}