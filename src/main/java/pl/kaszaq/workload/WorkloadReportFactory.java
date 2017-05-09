package pl.kaszaq.workload;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.kaszaq.agile.AgileClient;
import pl.kaszaq.agile.IssueData;
import static pl.kaszaq.agile.IssuePredicates.hasSubtasks;
import static pl.kaszaq.agile.IssuePredicates.inResolution;
import static pl.kaszaq.agile.IssuePredicates.inStatusOnDay;
import static pl.kaszaq.agile.IssuePredicates.isBlockedEntireDay;
import static pl.kaszaq.agile.IssuePredicates.isEpic;
import pl.kaszaq.agile.grouping.IssueHierarchyNode;
import pl.kaszaq.agile.grouping.IssueHierarchyNodeProvider;

/**
 *
 * @author michal.kasza
 */
@AllArgsConstructor
@Slf4j
public class WorkloadReportFactory {

    private final AgileClient agileClient;
    private final IssueHierarchyNodeProvider issueHierarchyNodeProvider;

    public Map<LocalDate, WorkloadDailyReport> calculateWorkload(LocalDate from, LocalDate to, String projectId, String... wipStatuses) {
        Map<LocalDate, WorkloadDailyReport> workloadReport = new HashMap<>();
        List<IssueData> issues = getValidIssues(projectId);
        LocalDate date = from;

        while (date.isBefore(to) || date.isEqual(to)) {
            WorkloadDailyReport dailyReport = new WorkloadDailyReport();
            issues.stream().filter(
                    inStatusOnDay(date, wipStatuses).and(isBlockedEntireDay(date).negate())
            )
                    .forEach(issue -> {
                        IssueHierarchyNode node = issueHierarchyNodeProvider.getHierarchy(issue);
                        Double value = 100.0;
                        Set<IssueData> leafIssues = node.getLeafsIssues();
                        double leafIssuesValue = value / leafIssues.size();
                        leafIssues.forEach(i -> {
                            dailyReport.reportWorkloadOnIssue(i, leafIssuesValue);
                        });
                    });
            workloadReport.put(date, dailyReport);

            date = date.plusDays(1);
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }
        }
        return workloadReport;

    }

    private List<IssueData> getValidIssues(String projectId) {
        return agileClient.getAgileProject(projectId).getAllIssues().stream()
                .filter(
                        inResolution("Won't Fix", "Cannot Reproduce", "Duplicate", "Incomplete", "Not an Issue", "Not Enough Information", "Retest", "Unresolved").negate()
                                .and(isEpic().negate())
                                .and(hasSubtasks().negate())
                )
                .collect(Collectors.toList());
    }
}
