package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Printer;
import com.aatechsolutions.elgransazon.domain.entity.PrinterType;

import java.util.List;
import java.util.Optional;

public interface PrinterService {

    List<Printer> findAll();

    Optional<Printer> findById(Long id);

    Optional<Printer> findByType(PrinterType type);

    /** Save (create or update). Enforces at most one printer per type per company. The same Windows name may be reused across roles. */
    Printer save(Printer printer);

    void deleteById(Long id);
}
