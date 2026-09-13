package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterRepository extends JpaRepository<Printer, Long> {

    List<Printer> findAllByCompanyOrderByPrinterTypeAsc(Company company);

    Optional<Printer> findByCompanyAndPrinterType(Company company, PrinterType printerType);

    Optional<Printer> findByIdAndCompany(Long id, Company company);

    boolean existsByCompanyAndPrinterType(Company company, PrinterType printerType);

    boolean existsByCompanyAndPrinterTypeAndIdNot(Company company, PrinterType printerType, Long id);
}
