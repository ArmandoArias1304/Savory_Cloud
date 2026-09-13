package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import com.aatechsolutions.elgransazon.domain.repository.PrinterRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PrinterServiceImpl implements PrinterService {

    private final PrinterRepository printerRepository;

    private Company currentCompany() {
        return CompanyContext.requireCurrentCompany();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Printer> findAll() {
        return printerRepository.findAllByCompanyOrderByPrinterTypeAsc(currentCompany());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Printer> findById(Long id) {
        return printerRepository.findByIdAndCompany(id, currentCompany());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Printer> findByType(PrinterType type) {
        return printerRepository.findByCompanyAndPrinterType(currentCompany(), type);
    }

    @Override
    public Printer save(Printer printer) {
        Company company = currentCompany();
        printer.setCompany(company);

        boolean isNew = (printer.getId() == null);

        if (isNew) {
            if (printerRepository.existsByCompanyAndPrinterType(company, printer.getPrinterType())) {
                throw new IllegalArgumentException(
                    "Ya hay una impresora asignada para Comandas de " + printer.getPrinterType().getDisplayName());
            }
        } else {
            if (printerRepository.existsByCompanyAndPrinterTypeAndIdNot(company, printer.getPrinterType(), printer.getId())) {
                throw new IllegalArgumentException(
                    "Ya hay una impresora asignada para Comandas de " + printer.getPrinterType().getDisplayName());
            }
            // Merge into existing managed entity to preserve audit fields
            Printer existing = printerRepository.findByIdAndCompany(printer.getId(), company)
                .orElseThrow(() -> new IllegalArgumentException("Impresora no encontrada"));
            existing.setName(printer.getName());
            existing.setPrinterType(printer.getPrinterType());
            existing.setIpAddress(printer.getIpAddress());
            return printerRepository.save(existing);
        }

        log.info("Saving printer '{}' type={} for company {}", printer.getName(), printer.getPrinterType(), company.getIdCompany());
        return printerRepository.save(printer);
    }

    @Override
    public void deleteById(Long id) {
        Printer printer = printerRepository.findByIdAndCompany(id, currentCompany())
            .orElseThrow(() -> new IllegalArgumentException("Impresora no encontrada"));
        log.info("Deleting printer '{}' id={}", printer.getName(), id);
        printerRepository.delete(printer);
    }
}
