package vn.gov.drvn.kcht.dto.report;

import java.util.List;

public record MaintenanceReportDto(
        MaintenanceSummary summary,
        List<MaintenanceProjectItem> projects,
        List<BranchBudgetSummary> branchSummary,
        List<YearlyBudgetSummary> yearlySummary
) {
    public record MaintenanceSummary(
            int totalProjects,
            long totalBudgetVnd,
            int completedCount,
            int inProgressCount,
            int plannedCount,
            double completionRatePercent
    ) {}

    public record MaintenanceProjectItem(
            String id,
            String projectCode,
            String projectName,
            String branchId,
            String branchName,
            String routeCode,
            String maintenanceType,
            long budgetVnd,
            int planYear,
            String status,
            String contractor,
            String startDate,
            String endDate
    ) {}

    public record BranchBudgetSummary(
            String branchId,
            String branchName,
            int projectCount,
            long totalBudgetVnd,
            double percent
    ) {}

    public record YearlyBudgetSummary(
            int year,
            int projectCount,
            long totalBudgetVnd
    ) {}
}
