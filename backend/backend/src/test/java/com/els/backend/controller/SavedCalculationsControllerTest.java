package com.els.backend.controller;

import com.els.backend.database.calculations.SavedCalculationStore;
import com.els.backend.service.FirebaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Unit tests for controller behavior without hitting a real database.
class SavedCalculationsControllerTest {

    private FirebaseAuthService firebaseAuthService;
    private SavedCalculationsController controller;
    private SavedCalculationStore savedCalculationStore;

    @BeforeEach
    void setUp() {
        firebaseAuthService = mock(FirebaseAuthService.class);
        savedCalculationStore = mock(SavedCalculationStore.class);
        controller = new SavedCalculationsController(firebaseAuthService, mock(com.els.backend.database.auth.AuthStore.class), savedCalculationStore);
    }

    @Test
    void list_missingAuthorization_returnsUnauthorized() {
        ResponseEntity<List<SavedCalculationsController.SavedCalculationResponse>> response =
                controller.list(null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void list_withUidBody_returnsOk() {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        SavedCalculationStore.SavedCalculation saved =
                new SavedCalculationStore.SavedCalculation(
                        42L,
                        testUid,
                        "VFIAX",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                );
        when(savedCalculationStore.listByUid(testUid)).thenReturn(List.of(saved));

        SavedCalculationsController.UidRequest request =
                new SavedCalculationsController.UidRequest(testUid);
        ResponseEntity<List<SavedCalculationsController.SavedCalculationResponse>> response =
                controller.list(null, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void create_update_delete_roundTrip() throws Exception {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        FirebaseAuthService.VerifiedFirebaseUser user =
                new FirebaseAuthService.VerifiedFirebaseUser(
                        testUid,
                        testUid + "@example.com",
                        "Test User",
                        null,
                        "password"
                );
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(user);

        SavedCalculationStore.SavedCalculation saved =
                new SavedCalculationStore.SavedCalculation(
                        42L,
                        testUid,
                        "VFIAX",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                );
        when(savedCalculationStore.insert(any(), any())).thenReturn(saved);
        when(savedCalculationStore.listByUid(testUid)).thenReturn(List.of(saved));
        when(savedCalculationStore.update(anyString(), any(), any())).thenReturn(java.util.Optional.of(
                new SavedCalculationStore.SavedCalculation(
                        42L,
                        testUid,
                        "FDGRX",
                        "FDGRX",
                        12000,
                        12,
                        1.1,
                        0.07,
                        24800,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                )
        ));
        when(savedCalculationStore.delete(testUid, 42L)).thenReturn(true);

        SavedCalculationsController.SavedCalculationRequest createRequest =
                new SavedCalculationsController.SavedCalculationRequest(
                        testUid,
                        null,
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500
                );
        ResponseEntity<SavedCalculationsController.SavedCalculationResponse> created =
                controller.create("Bearer test-token", createRequest);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        assertNotNull(created.getBody());
        assertEquals("VFIAX", created.getBody().ticker());

        SavedCalculationsController.SavedCalculationRequest updateRequest =
                new SavedCalculationsController.SavedCalculationRequest(
                        testUid,
                        null,
                        "FDGRX",
                        12000,
                        12,
                        1.1,
                        0.07,
                        24800
                );
        ResponseEntity<SavedCalculationsController.SavedCalculationResponse> updated =
                controller.update("Bearer test-token", saved.id(), updateRequest);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertNotNull(updated.getBody());
        assertEquals("FDGRX", updated.getBody().ticker());

        ResponseEntity<List<SavedCalculationsController.SavedCalculationResponse>> listResponse =
                controller.list("Bearer test-token", null);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());
        assertEquals(1, listResponse.getBody().size());
        assertEquals("FDGRX", listResponse.getBody().get(0).ticker());

        ResponseEntity<Void> deleted = controller.delete("Bearer test-token", saved.id());
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
    }

    @Test
    void update_missingRow_returnsNotFound() throws Exception {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        FirebaseAuthService.VerifiedFirebaseUser user =
                new FirebaseAuthService.VerifiedFirebaseUser(
                        testUid,
                        testUid + "@example.com",
                        "Test User",
                        null,
                        "password"
                );
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(user);
        when(savedCalculationStore.update(anyString(), any(), any())).thenReturn(java.util.Optional.empty());

        SavedCalculationsController.SavedCalculationRequest request =
                new SavedCalculationsController.SavedCalculationRequest(
                        testUid,
                        null,
                        "SWPPX",
                        5000,
                        5,
                        1.0,
                        0.05,
                        6400
                );
        ResponseEntity<SavedCalculationsController.SavedCalculationResponse> response =
                controller.update("Bearer test-token", 999999L, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void create_invalidPayload_returnsBadRequest() throws Exception {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        FirebaseAuthService.VerifiedFirebaseUser user =
                new FirebaseAuthService.VerifiedFirebaseUser(
                        testUid,
                        testUid + "@example.com",
                        "Test User",
                        null,
                        "password"
                );
        when(firebaseAuthService.verifyIdToken(anyString())).thenReturn(user);

        SavedCalculationsController.SavedCalculationRequest request =
                new SavedCalculationsController.SavedCalculationRequest(
                        testUid,
                        null,
                        "",
                        -100,
                        0,
                        1.2,
                        0.08,
                        21500
                );
        ResponseEntity<SavedCalculationsController.SavedCalculationResponse> response =
                controller.create("Bearer test-token", request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listSavedCalculations_withNameQuery_filters() {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        SavedCalculationStore.SavedCalculation saved =
                new SavedCalculationStore.SavedCalculation(
                        55L,
                        testUid,
                        "Retirement S&P 500",
                        "VFIAX",
                        10000,
                        10,
                        1.2,
                        0.08,
                        21500,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                );
        when(savedCalculationStore.listByUidAndNameLike(testUid, "Retirement"))
                .thenReturn(List.of(saved));

        SavedCalculationsController.UidRequest request =
                new SavedCalculationsController.UidRequest(testUid);
        ResponseEntity<List<SavedCalculationsController.SavedCalculationResponse>> response =
                controller.listSavedCalculations(request, "Retirement");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void patchSavedCalculation_missingRow_returnsNotFound() {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        when(savedCalculationStore.getById(testUid, 101L)).thenReturn(java.util.Optional.empty());

        SavedCalculationsController.SavedCalculationPatchRequest request =
                new SavedCalculationsController.SavedCalculationPatchRequest(
                        testUid,
                        "Name",
                        null,
                        null,
                        null
                );
        ResponseEntity<Object> response = controller.patchSavedCalculation(101L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteSavedCalculation_missingRow_returnsNotFound() {
        String testUid = "test-saved-calculation-" + UUID.randomUUID();
        when(savedCalculationStore.delete(testUid, 202L)).thenReturn(false);

        SavedCalculationsController.UidRequest request =
                new SavedCalculationsController.UidRequest(testUid);
        ResponseEntity<Object> response = controller.deleteSavedCalculation(202L, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
