package vn.gov.drvn.kcht.cli;

import org.junit.jupiter.api.Test;
import vn.gov.drvn.kcht.service.DatasetRegistryService;
import vn.gov.drvn.kcht.service.ImportService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ImportCliRunnerTest {
    @Test
    void applicationServerPropertiesDoNotTriggerCliExit() throws Exception {
        ImportService importer = mock(ImportService.class);
        DatasetRegistryService registry = mock(DatasetRegistryService.class);
        ImportCliRunner runner = new ImportCliRunner(importer, registry);

        runner.run("--server.port=8094", "--spring.profiles.active=dev");

        verifyNoInteractions(importer, registry);
    }
}
