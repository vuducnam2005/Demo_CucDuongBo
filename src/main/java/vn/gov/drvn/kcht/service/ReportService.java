package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.dto.report.MaintenanceReportDto;
import vn.gov.drvn.kcht.dto.report.RoadLengthReportDto;
import vn.gov.drvn.kcht.dto.report.RoadSignBlackspotReportDto;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Báo cáo chiều dài mạng lưới đường bộ (Road Lengths Report).
     * Truy vấn trực tiếp từ dataset mst_national_road và phân tích cấu trúc data_.
     */
    public RoadLengthReportDto getRoadLengthReport(String branch, String route, String surfaceType) {
        String sql = "SELECT record_key, raw_payload FROM raw_dataset_record WHERE dataset_key = 'mst_national_road'";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<RoadLengthReportDto.RouteLengthItem> allRoutes = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String recKey = (String) row.get("record_key");
            Object rawPayload = row.get("raw_payload");
            String payloadJson = rawPayload != null ? rawPayload.toString() : null;

            String routeCode = recKey;
            String routeName = "Quốc lộ " + recKey;
            Double lengthKm = null;
            String branchId = "cdb_vn";
            String sType = "Bê tông nhựa";
            String rClass = "Cấp III đồng bằng";

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.hasNonNull("fielddisplay")) {
                        routeCode = node.get("fielddisplay").asText();
                    }
                    if (node.hasNonNull("branch_id")) {
                        branchId = node.get("branch_id").asText();
                    }

                    if (node.hasNonNull("data_")) {
                        String dataStr = node.get("data_").asText();
                        JsonNode dataArray = objectMapper.readTree(dataStr);
                        if (dataArray.isArray()) {
                            for (JsonNode item : dataArray) {
                                String colId = item.path("column_identify").asText("");
                                String colVal = item.path("column_value").asText("");
                                if ("name_vi".equals(colId) && !colVal.isBlank()) {
                                    routeName = colVal;
                        } else if ("actual_length".equals(colId) && !colVal.isBlank()) {
                                    try {
                                        lengthKm = Double.parseDouble(colVal.trim());
                    } catch (NumberFormatException ex) {
                        log.warn("Ignoring invalid actual_length '{}' for road record {}", colVal, recKey);
                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Unable to parse national-road payload for record {}", recKey, ex);
                }
            }

            // A route without an actual length is not a measurable road-length row.
            // Excluding it keeps totals consistent with the source SQL aggregate.
            if (lengthKm == null || !Double.isFinite(lengthKm) || lengthKm < 0) {
                log.warn("Skipping road-length record with missing/invalid actual_length: {}", recKey);
                continue;
            }

            // Gán phân loại mặt đường và cấp đường mẫu dựa trên mã tuyến
            if (routeCode.contains("1") || routeCode.contains("5") || routeCode.contains("18")) {
                sType = "Bê tông nhựa cấp cao A1";
                rClass = "Cấp I - II đồng bằng";
            } else if (routeCode.contains("6") || routeCode.contains("7") || routeCode.contains("12")) {
                sType = "Bê tông nhựa thảm";
                rClass = "Cấp III - IV miền núi";
            }

            String branchName = mapBranchName(branchId);
            String mgmtUnit = "Chi cục Quản lý đường bộ " + branchId;

            allRoutes.add(new RoadLengthReportDto.RouteLengthItem(
                    routeCode, routeName, lengthKm, 1, branchId, branchName, sType, rClass, mgmtUnit
            ));
        }

        // Lọc theo điều kiện
        List<RoadLengthReportDto.RouteLengthItem> filteredRoutes = allRoutes.stream()
                .filter(r -> branch == null || branch.isBlank() || r.branchId().equalsIgnoreCase(branch.trim()))
                .filter(r -> route == null || route.isBlank() || r.routeCode().toUpperCase().contains(route.trim().toUpperCase()))
                .filter(r -> surfaceType == null || surfaceType.isBlank() || r.surfaceType().equalsIgnoreCase(surfaceType.trim()))
                .sorted(Comparator.comparingDouble(RoadLengthReportDto.RouteLengthItem::lengthKm).reversed())
                .collect(Collectors.toList());

        // Tổng hợp chỉ số
        int totalRoutes = filteredRoutes.size();
        int totalSegments = filteredRoutes.size();
        double totalLengthKm = Math.round(filteredRoutes.stream().mapToDouble(RoadLengthReportDto.RouteLengthItem::lengthKm).sum() * 10.0) / 10.0;
        double averageLengthKm = totalRoutes > 0 ? Math.round((totalLengthKm / totalRoutes) * 10.0) / 10.0 : 0.0;

        String longestCode = filteredRoutes.isEmpty() ? "—" : filteredRoutes.get(0).routeCode();
        double longestLength = filteredRoutes.isEmpty() ? 0.0 : filteredRoutes.get(0).lengthKm();

        RoadLengthReportDto.ReportSummary summary = new RoadLengthReportDto.ReportSummary(
                totalRoutes, totalSegments, totalLengthKm, averageLengthKm, longestCode, longestLength
        );

        // Phân bổ theo Khu vực quản lý (Branch Distribution)
        Map<String, List<RoadLengthReportDto.RouteLengthItem>> byBranch = filteredRoutes.stream()
                .collect(Collectors.groupingBy(RoadLengthReportDto.RouteLengthItem::branchId));

        List<RoadLengthReportDto.BranchDistributionItem> branchDist = byBranch.entrySet().stream()
                .map(e -> {
                    String bId = e.getKey();
                    String bName = mapBranchName(bId);
                    double bLen = Math.round(e.getValue().stream().mapToDouble(RoadLengthReportDto.RouteLengthItem::lengthKm).sum() * 10.0) / 10.0;
                    double percent = totalLengthKm > 0 ? Math.round((bLen / totalLengthKm) * 1000.0) / 10.0 : 0.0;
                    return new RoadLengthReportDto.BranchDistributionItem(bId, bName, e.getValue().size(), bLen, percent);
                })
                .sorted(Comparator.comparingDouble(RoadLengthReportDto.BranchDistributionItem::totalLengthKm).reversed())
                .collect(Collectors.toList());

        // Phân bổ theo Loại mặt đường (Surface Distribution)
        Map<String, List<RoadLengthReportDto.RouteLengthItem>> bySurface = filteredRoutes.stream()
                .collect(Collectors.groupingBy(RoadLengthReportDto.RouteLengthItem::surfaceType));

        List<RoadLengthReportDto.SurfaceDistributionItem> surfaceDist = bySurface.entrySet().stream()
                .map(e -> {
                    String sName = e.getKey();
                    double sLen = Math.round(e.getValue().stream().mapToDouble(RoadLengthReportDto.RouteLengthItem::lengthKm).sum() * 10.0) / 10.0;
                    double percent = totalLengthKm > 0 ? Math.round((sLen / totalLengthKm) * 1000.0) / 10.0 : 0.0;
                    return new RoadLengthReportDto.SurfaceDistributionItem(sName, sLen, percent);
                })
                .sorted(Comparator.comparingDouble(RoadLengthReportDto.SurfaceDistributionItem::totalLengthKm).reversed())
                .collect(Collectors.toList());

        return new RoadLengthReportDto(summary, filteredRoutes, branchDist, surfaceDist);
    }

    /**
     * Xuất báo cáo chiều dài đường dạng CSV với UTF-8 BOM.
     */
    public byte[] exportRoadLengthReportCsv(String branch, String route, String surfaceType) {
        RoadLengthReportDto report = getRoadLengthReport(branch, route, surfaceType);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM
        sb.append("STT,Mã tuyến,Tên tuyến đường,Chiều dài (km),Số đoạn,Khu vực quản lý,Loại mặt đường,Cấp đường,Đơn vị quản lý\n");

        int idx = 1;
        for (RoadLengthReportDto.RouteLengthItem item : report.routes()) {
            sb.append(idx++).append(",")
                    .append(escapeCsv(item.routeCode())).append(",")
                    .append(escapeCsv(item.routeName())).append(",")
                    .append(item.lengthKm()).append(",")
                    .append(item.segmentCount()).append(",")
                    .append(escapeCsv(item.branchName())).append(",")
                    .append(escapeCsv(item.surfaceType())).append(",")
                    .append(escapeCsv(item.roadClass())).append(",")
                    .append(escapeCsv(item.managementUnit())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Báo cáo Kế hoạch và Thực hiện Bảo trì Đường bộ (Maintenance Report).
     */
    public MaintenanceReportDto getMaintenanceReport(String branch, Integer year, String type, String status) {
        List<MaintenanceReportDto.MaintenanceProjectItem> allProjects = generateMaintenanceProjects();

        List<MaintenanceReportDto.MaintenanceProjectItem> filtered = allProjects.stream()
                .filter(p -> branch == null || branch.isBlank() || p.branchId().equalsIgnoreCase(branch.trim()))
                .filter(p -> year == null || p.planYear() == year)
                .filter(p -> type == null || type.isBlank() || p.maintenanceType().equalsIgnoreCase(type.trim()))
                .filter(p -> status == null || status.isBlank() || p.status().equalsIgnoreCase(status.trim()))
                .collect(Collectors.toList());

        int totalProjects = filtered.size();
        long totalBudget = filtered.stream().mapToLong(MaintenanceReportDto.MaintenanceProjectItem::budgetVnd).sum();
        int completed = (int) filtered.stream().filter(p -> "Hoàn thành".equalsIgnoreCase(p.status())).count();
        int inProgress = (int) filtered.stream().filter(p -> "Đang thi công".equalsIgnoreCase(p.status())).count();
        int planned = (int) filtered.stream().filter(p -> "Chuẩn bị đầu tư".equalsIgnoreCase(p.status()) || "Kế hoạch".equalsIgnoreCase(p.status())).count();
        double completionRate = totalProjects > 0 ? Math.round(((double) completed / totalProjects) * 1000.0) / 10.0 : 0.0;

        MaintenanceReportDto.MaintenanceSummary summary = new MaintenanceReportDto.MaintenanceSummary(
                totalProjects, totalBudget, completed, inProgress, planned, completionRate
        );

        // Tổng hợp kinh phí theo đơn vị (Branch Budget Summary)
        Map<String, List<MaintenanceReportDto.MaintenanceProjectItem>> byBranch = filtered.stream()
                .collect(Collectors.groupingBy(MaintenanceReportDto.MaintenanceProjectItem::branchId));

        List<MaintenanceReportDto.BranchBudgetSummary> branchSummary = byBranch.entrySet().stream()
                .map(e -> {
                    String bId = e.getKey();
                    String bName = mapBranchName(bId);
                    long bBudget = e.getValue().stream().mapToLong(MaintenanceReportDto.MaintenanceProjectItem::budgetVnd).sum();
                    double percent = totalBudget > 0 ? Math.round(((double) bBudget / totalBudget) * 1000.0) / 10.0 : 0.0;
                    return new MaintenanceReportDto.BranchBudgetSummary(bId, bName, e.getValue().size(), bBudget, percent);
                })
                .sorted(Comparator.comparingLong(MaintenanceReportDto.BranchBudgetSummary::totalBudgetVnd).reversed())
                .collect(Collectors.toList());

        // Tổng hợp theo Năm kế hoạch (Yearly Budget Summary)
        Map<Integer, List<MaintenanceReportDto.MaintenanceProjectItem>> byYear = filtered.stream()
                .collect(Collectors.groupingBy(MaintenanceReportDto.MaintenanceProjectItem::planYear));

        List<MaintenanceReportDto.YearlyBudgetSummary> yearlySummary = byYear.entrySet().stream()
                .map(e -> {
                    int y = e.getKey();
                    long yBudget = e.getValue().stream().mapToLong(MaintenanceReportDto.MaintenanceProjectItem::budgetVnd).sum();
                    return new MaintenanceReportDto.YearlyBudgetSummary(y, e.getValue().size(), yBudget);
                })
                .sorted(Comparator.comparingInt(MaintenanceReportDto.YearlyBudgetSummary::year).reversed())
                .collect(Collectors.toList());

        return new MaintenanceReportDto(summary, filtered, branchSummary, yearlySummary);
    }

    /**
     * Xuất báo cáo bảo trì ra tệp CSV với UTF-8 BOM.
     */
    public byte[] exportMaintenanceReportCsv(String branch, Integer year, String type, String status) {
        MaintenanceReportDto report = getMaintenanceReport(branch, year, type, status);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("STT,Mã dự án,Tên công trình/hạng mục bảo trì,Khu vực,Tuyến đường,Loại hình,Kinh phí (VNĐ),Năm kế hoạch,Trạng thái,Đơn vị thi công,Ngày bắt đầu,Ngày hoàn thành\n");

        int idx = 1;
        for (MaintenanceReportDto.MaintenanceProjectItem item : report.projects()) {
            sb.append(idx++).append(",")
                    .append(escapeCsv(item.projectCode())).append(",")
                    .append(escapeCsv(item.projectName())).append(",")
                    .append(escapeCsv(item.branchName())).append(",")
                    .append(escapeCsv(item.routeCode())).append(",")
                    .append(escapeCsv(item.maintenanceType())).append(",")
                    .append(item.budgetVnd()).append(",")
                    .append(item.planYear()).append(",")
                    .append(escapeCsv(item.status())).append(",")
                    .append(escapeCsv(item.contractor())).append(",")
                    .append(escapeCsv(item.startDate())).append(",")
                    .append(escapeCsv(item.endDate())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Thống kê biển báo hiệu đường bộ theo QCVN 41:2019/BGTVT và danh sách Điểm đen / Điểm tiềm ẩn TNGT.
     */
    public RoadSignBlackspotReportDto getRoadSignBlackspotReport(String branch, String route, String category) {
        long totalSigns = 222112L;

        // Phân loại biển báo theo QCVN 41:2019/BGTVT
        List<RoadSignBlackspotReportDto.SignCategoryItem> categories = List.of(
                new RoadSignBlackspotReportDto.SignCategoryItem("P", "Biển cấm (Prohibitory signs)", "P.", 64412, 29.0, "P.102, P.123", "Báo hiệu các điều cấm hoặc hạn chế mà người tham gia giao thông không được vi phạm"),
                new RoadSignBlackspotReportDto.SignCategoryItem("W", "Biển cảnh báo & nguy hiểm (Warning signs)", "W.", 55528, 25.0, "W.201, W.205", "Cảnh báo trước tình huống nguy hiểm trên đường (đoạn cong gấp, giao cắt, dốc cao)"),
                new RoadSignBlackspotReportDto.SignCategoryItem("R", "Biển hiệu lệnh (Mandatory signs)", "R.", 33316, 15.0, "R.301, R.302", "Báo các hiệu lệnh phải thi hành (hướng đi bắt buộc, tốc độ tối thiểu)"),
                new RoadSignBlackspotReportDto.SignCategoryItem("I", "Biển chỉ dẫn (Information signs)", "I.", 48864, 22.0, "I.401, I.414", "Chỉ dẫn hướng đi hoặc các điều cần biết nhằm giúp người tham gia giao thông thuận lợi"),
                new RoadSignBlackspotReportDto.SignCategoryItem("S", "Biển phụ & biển viết bằng chữ", "S.", 19992, 9.0, "S.501, S.503", "Thuyết minh bổ sung hoặc chỉ dẫn kèm theo các nhóm biển báo chính")
        );

        // Danh mục các điểm đen và điểm tiềm ẩn tai nạn giao thông trọng điểm
        List<RoadSignBlackspotReportDto.BlackspotItem> allBlackspots = List.of(
                new RoadSignBlackspotReportDto.BlackspotItem("bs_01", "DDB-QL1-01", "QL.1", "Km 592+300 - Km 593+100 (Đèo Ngang)", "cuc_ql_duong_bo_2", "Khu QLĐB II", "Rất nguy hiểm (Điểm đen)", "Đoạn đường đèo dốc quanh co bán kính hẹp, sương mù dày đặc", 12, 4, "Đang xử lý", 106.4258, 17.8921),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_02", "DDB-QL6-02", "QL.6", "Km 78+500 (Dốc Cun, Hòa Bình)", "cuc_ql_duong_bo_1", "Khu QLĐB I", "Rất nguy hiểm (Điểm đen)", "Dốc dài liên tục 10%, khúc cua gấp chữ U, thường xuyên mất phanh", 15, 6, "Đang xử lý", 105.3124, 20.7631),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_03", "DDB-QL14-03", "QL.14", "Km 1642+200 (Chư Sê, Gia Lai)", "cuc_ql_duong_bo_3", "Khu QLĐB III", "Nguy hiểm (Điểm tiềm ẩn)", "Giao cắt ngã tư tầm nhìn hạn chế, thiếu đèn tín hiệu và gờ giảm tốc", 8, 2, "Đang theo dõi", 108.0125, 13.7842),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_04", "DDB-QL5-04", "QL.5", "Km 18+200 (Như Quỳnh, Hưng Yên)", "cuc_ql_duong_bo_1", "Khu QLĐB I", "Nguy hiểm (Điểm tiềm ẩn)", "Lưu lượng xe công-ten-nơ dày đặc, nhiều đường nhánh dân sinh mở tự phát", 9, 3, "Đã xử lý", 105.9874, 20.9781),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_05", "DDB-QL1A-05", "QL.1", "Km 1718+000 (Hàm Thuận Nam, Bình Thuận)", "cuc_ql_duong_bo_4", "Khu QLĐB IV", "Rất nguy hiểm (Điểm đen)", "Đoạn thẳng tốc độ cao thiếu dải phân cách cứng giữa hai chiều", 11, 5, "Đang xử lý", 107.9654, 11.0254),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_06", "DDB-QL18-06", "QL.18", "Km 84+100 (Uông Bí, Quảng Ninh)", "cuc_ql_duong_bo_1", "Khu QLĐB I", "Nguy hiểm (Điểm tiềm ẩn)", "Đoạn đường hẹp qua khu vực đông dân cư gần trường học", 6, 1, "Đã xử lý", 106.7782, 21.0421),
                new RoadSignBlackspotReportDto.BlackspotItem("bs_07", "DDB-QL20-07", "QL.20", "Km 110+400 (Đèo Bảo Lộc, Lâm Đồng)", "cuc_ql_duong_bo_4", "Khu QLĐB IV", "Rất nguy hiểm (Điểm đen)", "Đèo dốc quanh co trơn trượt mùa mưa, sạt lở taluy âm", 14, 5, "Đang xử lý", 107.7541, 11.4521)
        );

        List<RoadSignBlackspotReportDto.BlackspotItem> filteredSpots = allBlackspots.stream()
                .filter(b -> branch == null || branch.isBlank() || b.branchId().equalsIgnoreCase(branch.trim()))
                .filter(b -> route == null || route.isBlank() || b.routeCode().equalsIgnoreCase(route.trim()))
                .filter(b -> category == null || category.isBlank() || b.severity().contains(category.trim()))
                .collect(Collectors.toList());

        int totalSpots = filteredSpots.size();
        int highRisk = (int) filteredSpots.stream().filter(b -> b.severity().contains("Điểm đen")).count();
        int rectified = (int) filteredSpots.stream().filter(b -> "Đã xử lý".equalsIgnoreCase(b.rectificationStatus())).count();
        int monitored = (int) filteredSpots.stream().filter(b -> "Đang theo dõi".equalsIgnoreCase(b.rectificationStatus())).count();

        RoadSignBlackspotReportDto.OverviewSummary summary = new RoadSignBlackspotReportDto.OverviewSummary(
                totalSigns, categories.size(), totalSpots, highRisk, rectified, monitored
        );

        // Thống kê phân bổ biển báo và điểm đen theo Chi cục quản lý
        List<RoadSignBlackspotReportDto.BranchSignStatistics> branchStats = List.of(
                new RoadSignBlackspotReportDto.BranchSignStatistics("cuc_ql_duong_bo_1", "Khu QLĐB I (Bắc Bộ)", 72450L, 3),
                new RoadSignBlackspotReportDto.BranchSignStatistics("cuc_ql_duong_bo_2", "Khu QLĐB II (Bắc Trung Bộ)", 59840L, 1),
                new RoadSignBlackspotReportDto.BranchSignStatistics("cuc_ql_duong_bo_3", "Khu QLĐB III (Nam Trung Bộ)", 43222L, 1),
                new RoadSignBlackspotReportDto.BranchSignStatistics("cuc_ql_duong_bo_4", "Khu QLĐB IV (Nam Bộ)", 46600L, 2)
        );

        return new RoadSignBlackspotReportDto(summary, categories, filteredSpots, branchStats);
    }

    /**
     * Xuất danh sách Điểm đen và Thống kê Biển báo ra tệp CSV có UTF-8 BOM.
     */
    public byte[] exportRoadSignBlackspotReportCsv(String branch, String route, String category) {
        RoadSignBlackspotReportDto report = getRoadSignBlackspotReport(branch, route, category);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("STT,Mã điểm đen,Tuyến đường,Lý trình / Vị trí,Khu vực quản lý,Mức độ nguy hiểm,Mô tả nguyên nhân,Số vụ TNGT,Số người chết,Tình trạng xử lý,Kinh độ,Vĩ độ\n");

        int idx = 1;
        for (RoadSignBlackspotReportDto.BlackspotItem item : report.blackspots()) {
            sb.append(idx++).append(",")
                    .append(escapeCsv(item.spotCode())).append(",")
                    .append(escapeCsv(item.routeCode())).append(",")
                    .append(escapeCsv(item.kmMarker())).append(",")
                    .append(escapeCsv(item.branchName())).append(",")
                    .append(escapeCsv(item.severity())).append(",")
                    .append(escapeCsv(item.description())).append(",")
                    .append(item.incidentCount()).append(",")
                    .append(item.fatalityCount()).append(",")
                    .append(escapeCsv(item.rectificationStatus())).append(",")
                    .append(item.longitude() != null ? item.longitude() : "").append(",")
                    .append(item.latitude() != null ? item.latitude() : "").append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // Helper sinh dữ liệu danh mục kế hoạch bảo trì
    private List<MaintenanceReportDto.MaintenanceProjectItem> generateMaintenanceProjects() {
        return List.of(
                new MaintenanceReportDto.MaintenanceProjectItem("m_01", "BT-2026-001", "Sửa chữa định kỳ mặt đường bê tông nhựa Km 12 - Km 25 QL.1", "cuc_ql_duong_bo_1", "Khu QLĐB I", "QL.1", "Sửa chữa định kỳ", 14500000000L, 2026, "Đang thi công", "Công ty CP Xây dựng Cầu đường 1", "2026-02-15", "2026-08-30"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_02", "BT-2026-002", "Gia cố mái taluy chống sạt lở mùa mưa bão Dốc Cun Km 78 QL.6", "cuc_ql_duong_bo_1", "Khu QLĐB I", "QL.6", "Sửa chữa đột xuất", 8200000000L, 2026, "Chuẩn bị đầu tư", "Tổng Công ty XDCT Giao thông 8", "2026-04-01", "2026-10-15"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_03", "BT-2026-003", "Thay thế khe co giãn và thảm lại bê tông nhựa mặt cầu Đò Lèn Km 302 QL.1", "cuc_ql_duong_bo_2", "Khu QLĐB II", "QL.1", "Sửa chữa định kỳ", 6800000000L, 2026, "Hoàn thành", "Công ty TNHH MTV QL&XD Đường bộ Thanh Hóa", "2026-01-10", "2026-05-20"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_04", "BT-2026-004", "Sơn dặm vạch kẻ đường phản quang và thay thế biển báo hư hỏng QL.14", "cuc_ql_duong_bo_3", "Khu QLĐB III", "QL.14", "Bảo dưỡng thường xuyên", 3200000000L, 2026, "Hoàn thành", "Công ty CP Quản lý Đường bộ Gia Lai", "2026-01-05", "2026-04-15"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_05", "BT-2026-005", "Nạo vét hệ thống rãnh dọc, cống thoát nước ngang Km 10 - Km 50 QL.20", "cuc_ql_duong_bo_4", "Khu QLĐB IV", "QL.20", "Bảo dưỡng thường xuyên", 2800000000L, 2026, "Đang thi công", "Công ty CP Xây dựng Giao thông Đồng Nai", "2026-03-01", "2026-07-30"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_06", "BT-2025-010", "Xử lý điểm đen TNGT ngã tư Km 18+200 QL.5 (Như Quỳnh, Hưng Yên)", "cuc_ql_duong_bo_1", "Khu QLĐB I", "QL.5", "Xử lý điểm đen TNGT", 5400000000L, 2025, "Hoàn thành", "Công ty CP Phát triển Hạ tầng Giao thông 1", "2025-06-10", "2025-11-20"),
                new MaintenanceReportDto.MaintenanceProjectItem("m_07", "BT-2025-012", "Cải tạo nút giao và lắp đặt hệ thống đèn tín hiệu giao thông QL.1A Phan Thiết", "cuc_ql_duong_bo_4", "Khu QLĐB IV", "QL.1", "Xử lý điểm đen TNGT", 7600000000L, 2025, "Hoàn thành", "Công ty CP Giao thông Bình Thuận", "2025-08-01", "2025-12-25")
        );
    }

    private String mapBranchName(String branchId) {
        if (branchId == null) return "Cục Đường bộ Việt Nam";
        return switch (branchId.toLowerCase()) {
            case "cuc_ql_duong_bo_1", "kqldb_1" -> "Khu QLĐB I (Bắc Bộ)";
            case "cuc_ql_duong_bo_2", "kqldb_2" -> "Khu QLĐB II (Bắc Trung Bộ)";
            case "cuc_ql_duong_bo_3", "kqldb_3" -> "Khu QLĐB III (Nam Trung Bộ)";
            case "cuc_ql_duong_bo_4", "kqldb_4" -> "Khu QLĐB IV (Nam Bộ)";
            default -> "Cục Đường bộ Việt Nam (" + branchId + ")";
        };
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}
