package com.payroll.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.regulation.api.WageTypeResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes payrun results to a local {@code results/} directory (one JSON file per employee).
 * The results directory is ignored by git (see repo root .gitignore).
 */
public class LocalResultWriter {

    /**
     * Resolves results directory: "results" from cwd, or "../results" when cwd is engine/.
     */
    public Path resolveResultsDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path results = cwd.resolve("results");
        if (cwd.getFileName() != null && "engine".equals(cwd.getFileName().toString()) && !Files.exists(results)) {
            results = cwd.resolve("../results").normalize();
        }
        return results;
    }

    /**
     * Writes one JSON file per employee under {@code resultsDir}.
     */
    public void writeResultJson(ObjectMapper mapper, Path resultsDir, StubDataRecord rec,
                                List<WageTypeResult> results, Map<Integer, String> wageTypeNames) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("employeeId", rec.getEmployeeId());
        out.put("tenantId", rec.getTenantId());
        out.put("periodStart", rec.getPeriodStart());
        out.put("periodEnd", rec.getPeriodEnd());
        Map<String, BigDecimal> wageTypesByNumber = results.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.wageTypeNumber()), WageTypeResult::value, (a, b) -> a, LinkedHashMap::new));
        out.put("wageTypes", wageTypesByNumber);
        List<Map<String, Object>> wageTypeResults = new ArrayList<>();
        for (WageTypeResult r : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("wageTypeNumber", r.wageTypeNumber());
            entry.put("wageTypeName", wageTypeNames.getOrDefault(r.wageTypeNumber(), ""));
            entry.put("value", r.value());
            wageTypeResults.add(entry);
        }
        out.put("wageTypeResults", wageTypeResults);
        Path file = resultsDir.resolve(rec.getEmployeeId() + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), out);
    }
}
