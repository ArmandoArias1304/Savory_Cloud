package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeShiftHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of EmployeeShiftHistoryService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmployeeShiftHistoryServiceImpl implements EmployeeShiftHistoryService {

    private final EmployeeShiftHistoryRepository historyRepository;

    @Override
    public List<EmployeeShiftHistory> getAllHistory() {
        log.debug("Fetching all shift history");
        return historyRepository.findAllOrderByActionDateDesc();
    }

    @Override
    public List<EmployeeShiftHistory> getRecentHistory(int limit) {
        log.debug("Fetching recent shift history (limit: {})", limit);
        return historyRepository.findRecentHistory(limit);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByEmployee(Long employeeId) {
        log.debug("Fetching history for employee ID: {}", employeeId);
        return historyRepository.findByEmployeeIdOrderByActionDateDesc(employeeId);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByShift(Long shiftId) {
        log.debug("Fetching history for shift ID: {}", shiftId);
        return historyRepository.findByShiftIdOrderByActionDateDesc(shiftId);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByEmployeeAndShift(Long employeeId, Long shiftId) {
        log.debug("Fetching history for employee ID: {} and shift ID: {}", employeeId, shiftId);
        return historyRepository.findByEmployeeIdAndShiftId(employeeId, shiftId);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByAction(ShiftAction action) {
        log.debug("Fetching history by action: {}", action);
        return historyRepository.findByAction(action);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByActionBy(Long actionById) {
        log.debug("Fetching history by action performed by ID: {}", actionById);
        return historyRepository.findByActionById(actionById);
    }

    @Override
    public List<EmployeeShiftHistory> getHistoryByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching history between {} and {}", startDate, endDate);
        return historyRepository.findByActionDateBetween(startDate, endDate);
    }

    @Override
    @Transactional
    public EmployeeShiftHistory createHistoryRecord(
            Employee employee,
            Shift shift,
            ShiftAction action,
            Employee actionBy,
            String reason) {
        
        log.info("Creating history record: {} - {}", employee.getFullName(), action);
        
        EmployeeShiftHistory history = EmployeeShiftHistory.builder()
                .employee(employee)
                .shift(shift)
                .action(action)
                .actionBy(actionBy)
                .actionDate(LocalDateTime.now())
                .reason(reason)
                .build();
        
        EmployeeShiftHistory saved = historyRepository.save(history);
        log.debug("History record created with ID: {}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public List<EmployeeShiftHistory> createHistoryRecords(
            List<Employee> employees, 
            Shift shift,
            ShiftAction action, 
            Employee actionBy, 
            String reason) {
        
        log.info("Creating {} history records for action: {}", employees.size(), action);
        
        List<EmployeeShiftHistory> histories = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Employee employee : employees) {
            EmployeeShiftHistory history = EmployeeShiftHistory.builder()
                    .employee(employee)
                    .shift(shift)
                    .action(action)
                    .actionBy(actionBy)
                    .actionDate(now)
                    .reason(reason)
                    .build();
            
            histories.add(historyRepository.save(history));
        }

        log.info("Successfully created {} history records", histories.size());
        return histories;
    }

    @Override
    public long countHistoryByEmployee(Long employeeId) {
        return historyRepository.countByEmployeeId(employeeId);
    }

    @Override
    public long countHistoryByShift(Long shiftId) {
        return historyRepository.countByShiftId(shiftId);
    }

    @Override
    public EmployeeShiftHistory getMostRecentAction(Long employeeId, Long shiftId) {
        log.debug("Getting most recent action for employee ID: {} and shift ID: {}", employeeId, shiftId);
        return historyRepository.findMostRecentActionForEmployeeAndShift(employeeId, shiftId);
    }

    @Override
    public Map<String, Object> getShiftChangesReport(LocalDateTime start, LocalDateTime end) {
        log.info("Generating shift changes report from {} to {}", start, end);
        
        List<EmployeeShiftHistory> history = historyRepository.findByActionDateBetween(start, end);
        
        long totalChanges = history.size();
        long assignments = history.stream().filter(h -> h.getAction() == ShiftAction.ASSIGNED).count();
        long removals = history.stream().filter(h -> h.getAction() == ShiftAction.REMOVED).count();
        
        Map<String, Object> report = new HashMap<>();
        report.put("startDate", start);
        report.put("endDate", end);
        report.put("totalChanges", totalChanges);
        report.put("assignments", assignments);
        report.put("removals", removals);
        report.put("details", history);
        
        return report;
    }

    @Override
    public Map<String, Object> getEmployeeShiftStatistics(Long employeeId) {
        log.info("Generating shift statistics for employee ID: {}", employeeId);
        
        List<EmployeeShiftHistory> history = historyRepository.findByEmployeeIdOrderByActionDateDesc(employeeId);
        
        long totalChanges = history.size();
        long assignments = history.stream().filter(h -> h.getAction() == ShiftAction.ASSIGNED).count();
        long removals = history.stream().filter(h -> h.getAction() == ShiftAction.REMOVED).count();
        
        // Get unique shifts
        Set<Long> uniqueShifts = history.stream()
                .map(h -> h.getShift().getId())
                .collect(Collectors.toSet());
        
        // Get most recent action
        EmployeeShiftHistory mostRecent = history.isEmpty() ? null : history.get(0);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("employeeId", employeeId);
        stats.put("totalChanges", totalChanges);
        stats.put("assignments", assignments);
        stats.put("removals", removals);
        stats.put("uniqueShifts", uniqueShifts.size());
        stats.put("mostRecentAction", mostRecent);
        stats.put("history", history);
        
        return stats;
    }
}
