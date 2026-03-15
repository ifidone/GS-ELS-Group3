package com.els.backend.service;

public interface AuthSyncUserStore {

    SyncResult syncVerifiedUser(FirebaseAuthService.VerifiedFirebaseUser user);

    record SyncResult(String status, String message) {
    }
}
