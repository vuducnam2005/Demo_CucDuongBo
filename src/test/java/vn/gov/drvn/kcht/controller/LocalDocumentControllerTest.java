package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;
import vn.gov.drvn.kcht.storage.StorageService;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LocalDocumentControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final StorageService storage = mock(StorageService.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final LocalDocumentController controller = new LocalDocumentController(
            database, storage, transaction);

    @Test
    void deniesAccountsWithoutBranchAndReadonlyUploads() throws IOException {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.folders(principal("ROLE_MANAGER", null))).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.upload(principal("ROLE_VIEWER", "region-1"),
                        new MockMultipartFile("file", "demo.pdf", "application/pdf", new byte[]{1}),
                        null, null, null)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.createFolder(principal("ROLE_EDITOR", "region-1"), "demo", null, null))
                .getStatusCode().value());
        verifyNoInteractions(database, storage);
    }

    @Test
    void rejectsUnsafeRenameAndInvalidIdBeforeReadingAnyFile() {
        UserPrincipal manager = principal("ROLE_MANAGER", "region-1");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.rename(manager, "1", new LocalDocumentController.RenameRequest("../bad.pdf")))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.document(manager, "../100")).getStatusCode().value());
        verifyNoInteractions(database, storage);
    }

    @Test
    void deniesUnauthorizedVersionsAndInvalidVersionNumbers() throws IOException {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.replace(principal("ROLE_VIEWER", "region-1"), "1",
                        new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[]{1}), null))
                .getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.versions(principal("ROLE_MANAGER", null), "1"))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.downloadVersion(principal("ROLE_MANAGER", "region-1"), "1", "0"))
                .getStatusCode().value());
        verifyNoInteractions(database, storage);
    }

    @Test
    void onlyManagersCanManageFoldersAndNamesMustBeValid() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.renameFolder(principal("ROLE_EDITOR", "region-1"), "11",
                        new LocalDocumentController.RenameRequest("Hồ sơ mới"))).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.deleteFolder(principal("ROLE_VIEWER", "region-1"), "11"))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.renameFolder(principal("ROLE_MANAGER", "region-1"), "11",
                        new LocalDocumentController.RenameRequest("../khác"))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.deleteFolder(principal("ROLE_MANAGER", "region-1"), "-1"))
                .getStatusCode().value());
        verifyNoInteractions(database, storage, transaction);
    }

    @Test
    void managerCannotChangeFolderOutsideTheirBranch() {
        executeTransactions();
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.renameFolder(principal("ROLE_MANAGER", "region-1"), "11",
                        new LocalDocumentController.RenameRequest("Tên mới"))).getStatusCode().value());
        verify(database).query(contains("f.organization_id = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<LocalDocumentController.Folder>>any(),
                eq(11L), eq("ROLE_MANAGER"), eq("region-1"));
        verifyNoMoreInteractions(database);
    }

    @Test
    void renameFolderKeepsIdentityAndWritesAudit() {
        executeTransactions();
        LocalDocumentController.Folder original = new LocalDocumentController.Folder(
                "11", "folder-11", "Tên cũ", "#", 2, "region-1");
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<LocalDocumentController.Folder>>any(),
                eq(11L), eq("ROLE_MANAGER"), eq("region-1"))).thenReturn(List.of(original));
        LocalDocumentController.Folder result = controller.renameFolder(
                principal("ROLE_MANAGER", "region-1"), "11",
                new LocalDocumentController.RenameRequest("  Tên mới  "));
        assertEquals("Tên mới", result.folderName());
        assertEquals("folder-11", result.folderCode());
        verify(database).update("UPDATE document_folder SET folder_name = ? WHERE id = ?", "Tên mới", 11L);
        verify(database).update(contains("'UPDATE', 'DOCUMENT_FOLDER'"),
                eq(1L), eq("demo"), eq("11"), eq("Tên cũ"), eq("Tên mới"));
    }

    @Test
    void deleteFolderRejectsChildrenAndOnlySoftDeletesEmptyFolders() {
        executeTransactions();
        LocalDocumentController.Folder original = new LocalDocumentController.Folder(
                "11", "folder-11", "Hồ sơ cũ", "#", 0, "region-1");
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<LocalDocumentController.Folder>>any(),
                eq(11L), eq("ROLE_MANAGER"), eq("region-1"))).thenReturn(List.of(original));
        when(database.queryForObject(anyString(), eq(Boolean.class), eq(11L), eq(11L)))
                .thenReturn(true, false);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.deleteFolder(principal("ROLE_MANAGER", "region-1"), "11"))
                .getStatusCode().value());
        verify(database, never()).update(contains("SET is_deleted = TRUE"), eq(11L));
        assertEquals(204, controller.deleteFolder(principal("ROLE_MANAGER", "region-1"), "11")
                .getStatusCode().value());
        verify(database).update("UPDATE document_folder SET is_deleted = TRUE WHERE id = ?", 11L);
        verify(database).update(contains("'DELETE', 'DOCUMENT_FOLDER'"),
                eq(1L), eq("demo"), eq("11"), eq("Hồ sơ cũ"));
    }

    private void executeTransactions() {
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private UserPrincipal principal(String role, String branch) {
        return new UserPrincipal(1L, "demo", null, null, "Demo", role, role,
                null, branch, true, List.of(), List.of());
    }
}
