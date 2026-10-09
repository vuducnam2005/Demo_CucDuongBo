package vn.gov.drvn.kcht.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.service.DatasetRegistryService;
import vn.gov.drvn.kcht.service.ImportService;

import java.util.Arrays;
import java.util.List;


@Component
@Profile("!test")
public class ImportCliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportCliRunner.class);

    private final ImportService importService;
    private final DatasetRegistryService registryService;

    public ImportCliRunner(ImportService importService, DatasetRegistryService registryService) {
        this.importService = importService;
        this.registryService = registryService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            log.info("Khởi động ứng dụng chế độ chờ lệnh. Sử dụng tham số --dry-run để chạy kiểm tra.");
            return;
        }

        boolean dryRun = false;
        boolean syncRegistry = false;
        boolean allFiles = false;
        String singleFile = null;
        String multipleFiles = null;
        String filter = null;
        int limit = 0;

        for (String arg : args) {
            if ("--dry-run".equalsIgnoreCase(arg)) {
                dryRun = true;
            } else if ("--sync-registry".equalsIgnoreCase(arg)) {
                syncRegistry = true;
            } else if ("--all".equalsIgnoreCase(arg) || "--full".equalsIgnoreCase(arg)) {
                allFiles = true;
            } else if (arg.startsWith("--file=")) {
                singleFile = arg.substring("--file=".length()).trim();
            } else if (arg.startsWith("--files=")) {
                multipleFiles = arg.substring("--files=".length()).trim();
            } else if (arg.startsWith("--filter=")) {
                filter = arg.substring("--filter=".length()).trim();
            } else if (arg.startsWith("--limit=")) {
                try {
                    limit = Integer.parseInt(arg.substring("--limit=".length()).trim());
                } catch (NumberFormatException e) {
                    log.warn("Tham số --limit không hợp lệ, bỏ qua.");
                }
            } else if ("--help".equalsIgnoreCase(arg)) {
                printHelp();
                return;
            }
        }

        if (!syncRegistry && !allFiles && singleFile == null && multipleFiles == null
                && filter == null && !dryRun) {
            return;
        }

        log.info("==============================================================================");
        log.info("CLI IMPORT SERVICE - CỤC ĐƯỜNG BỘ VIỆT NAM");
        log.info("Tham số: all={}, dryRun={}, syncRegistry={}, file={}, files={}, filter={}, limit={}",
                allFiles, dryRun, syncRegistry, singleFile, multipleFiles, filter, limit);
        log.info("==============================================================================");

        if (syncRegistry) {
            log.info(">>> Tiến hành đồng bộ dataset registry từ manifest...");
            registryService.syncRegistryFromManifest();
            log.info(">>> Hoàn tất đồng bộ dataset registry!");
        }

        if (allFiles) {
            log.info(">>> Thực thi nạp TOÀN BỘ dữ liệu từ manifest.json (DryRun: {}, Limit: {})", dryRun, limit);
            ImportService.ImportSummary summary = importService.importFromManifest(dryRun, null, limit);
            System.out.println("\n" + summary.toFormattedTable() + "\n");
        } else if (multipleFiles != null && !multipleFiles.isEmpty()) {
            List<String> fileList = Arrays.stream(multipleFiles.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            log.info(">>> Thực thi nạp danh sách {} tệp: {} (DryRun: {}, Limit: {})", fileList.size(), fileList, dryRun, limit);
            ImportService.ImportSummary summary = importService.importFiles(fileList, dryRun, limit);
            System.out.println("\n" + summary.toFormattedTable() + "\n");
        } else if (singleFile != null && !singleFile.isEmpty()) {
            log.info(">>> Thực thi nạp tệp đơn: {} (DryRun: {}, Limit: {})", singleFile, dryRun, limit);
            ImportService.ImportSummary summary = importService.importSingleFile(singleFile, dryRun, limit);
            System.out.println("\n" + summary.toFormattedTable() + "\n");
        } else if (filter != null) {
            log.info(">>> Thực thi nạp theo bộ lọc: {} (DryRun: {}, Limit: {})", filter, dryRun, limit);
            ImportService.ImportSummary summary = importService.importFromManifest(dryRun, filter, limit);
            System.out.println("\n" + summary.toFormattedTable() + "\n");
        } else if (dryRun) {
            log.info(">>> Chế độ --dry-run mặc định: Đang chạy kiểm thử với tệp mẫu nhỏ 'assets/duonggom.json'...");
            ImportService.ImportSummary summary = importService.importSingleFile("assets/duonggom.json", true, 10);
            System.out.println("\n" + summary.toFormattedTable() + "\n");
        }

        // Thoát ứng dụng sau khi hoàn thành lệnh CLI
        System.exit(0);
    }

    private void printHelp() {
        System.out.println("""
            ==============================================================================
            HƯỚNG DẪN SỬ DỤNG CLI IMPORT SERVICE:
            ==============================================================================
            --dry-run               : Chạy chế độ mô phỏng, chỉ stream và validate, không ghi CSDL
            --sync-registry         : Đồng bộ manifest.json và field_dictionary.json vào CSDL
            --file=<path>           : Chỉ định tệp cần xử lý (VD: --file=assets/duonggom.json)
            --files=<p1>,<p2>       : Chỉ định danh sách tệp phân tách bằng dấu phẩy
            --filter=<substring>    : Lọc các tệp trong manifest có đường dẫn chứa chuỗi này
            --limit=<number>        : Giới hạn số bản ghi xử lý trong mỗi tệp
            --help                  : Hiển thị hướng dẫn này
            
            Ví dụ:
              java -jar app.jar --dry-run --file=assets/duonggom.json --limit=10
              java -jar app.jar --files=assets/mst_national_road.json,assets/tbl_bridge.json
              java -jar app.jar --sync-registry
            ==============================================================================
            """);
    }
}

