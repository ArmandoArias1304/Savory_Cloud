package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWaste;
import com.aatechsolutions.elgransazon.domain.entity.IngredientWasteReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IngredientWasteService {

    IngredientWaste recordWaste(Long ingredientId,
                                BigDecimal quantity,
                                IngredientWasteReason reason,
                                LocalDate occurredOn,
                                String note,
                                Employee recordedBy);

    List<IngredientWaste> findRecent(int limit);
}
