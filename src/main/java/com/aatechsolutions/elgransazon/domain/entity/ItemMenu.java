package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * ItemMenu entity representing menu items/dishes offered by the restaurant
 * Examples: "Hamburguesa Clásica", "Coca-Cola", "Ensalada César", etc.
 * Each item has a recipe (list of ingredients) and automatically manages inventory
 */
@Entity
@Table(name = "item_menu", uniqueConstraints = {
    @UniqueConstraint(name = "uk_item_menu_name_company", columnNames = {"name", "company_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idItemMenu"})
@ToString(exclude = {"company", "category", "ingredients", "promotions", "availableComplements", "availabilityDays", "comboItems", "parentItem", "sizeItems"})
public class ItemMenu implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_menu")
    private Long idItemMenu;

    // ========== Company Relationship (Multi-Tenant) ==========
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank(message = "El nombre del platillo es requerido")
    @Size(min = 2, max = 200, message = "El nombre debe tener entre 2 y 200 caracteres")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========== Pricing ==========

    @NotNull(message = "El precio es requerido")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    // ========== Image ==========

    @Size(max = 500, message = "La URL de la imagen no puede exceder 500 caracteres")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ========== Status ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Indicates if the item is currently available for sale
     * Calculated automatically based on ingredient stock availability
     */
    @Column(name = "available", nullable = false)
    @Builder.Default
    private Boolean available = true;

    /**
     * Indicates if this item requires preparation by the chef
     * TRUE: Items like pizzas, burgers, hot dishes (chef must prepare)
     * FALSE: Items like sodas, bottled drinks, pre-packaged desserts (ready to serve)
     * Items with requiresPreparation=false will skip the chef and go directly to READY status
     */
    @Column(name = "requires_preparation", nullable = false)
    @Builder.Default
    private Boolean requiresPreparation = true;

    /**
     * Indicates if this item requires preparation by the barista
     * TRUE: Items like coffee, espresso, cappuccino, smoothies (barista must prepare)
     * FALSE: Items that don't require barista preparation
     * Items with requiresBaristaPreparation=true will be assigned to baristas
     */
    @Column(name = "requires_barista_preparation", nullable = false)
    @Builder.Default
    private Boolean requiresBaristaPreparation = false;

    /**
     * Indicates if this item requires preparation by the parrillero (grill cook)
     * TRUE: Items like grilled steaks, BBQ, asados (parrillero must prepare)
     * FALSE: Items that don't require parrillero preparation
     * Mutually exclusive with requiresPreparation (chef) and requiresBaristaPreparation.
     */
    @Column(name = "requires_parrillero_preparation", nullable = false)
    @Builder.Default
    private Boolean requiresParrilleroPreparation = false;

    /**
     * Indicates if this item requires a recipe (ingredient list) for stock management.
     * TRUE: Item has at least one ingredient; stock is discounted on order accept.
     * FALSE: Item has no ingredients (e.g., buffet plates, bread, items with no stock tracking).
     *        No stock discount happens on accept.
     * Independent of {@link #dineInOnly}: a "buffet" item is modeled as
     * requiresIngredients=false AND dineInOnly=true.
     * For combos this flag is always forced to false (combos use child items, not ingredients).
     */
    @Column(name = "requires_ingredients", nullable = false)
    @Builder.Default
    private Boolean requiresIngredients = true;

    /**
     * Indicates if this item is a combo item.
     * TRUE: Combo items contain other ItemMenu items instead of ingredients.
     *       They act as intermediaries: the combo itself is not sent to chef/barista,
     *       only the child items are sent to their respective preparers.
     *       The combo price overrides child item individual prices.
     * FALSE: Regular items with their own recipe/ingredients.
     */
    @Column(name = "is_combo", nullable = false)
    @Builder.Default
    private Boolean isCombo = false;

    /**
     * Indicates if this item is only available for dine-in consumption.
     * TRUE: Item only appears for DINE_IN orders (e.g., items served on plates that can't be packed).
     * FALSE: Item is available for all order types (DINE_IN, TAKEOUT, DELIVERY).
     */
    @Column(name = "dine_in_only", nullable = false)
    @Builder.Default
    private Boolean dineInOnly = false;

    /**
     * Maximum number of sauces that can be selected for this item
     * NULL or 0: No sauces allowed or unlimited (depends on business logic)
     * > 0: Maximum number of different sauces the customer can choose
     * Example: Boneless 250gr might have maxSauces=2 (can choose 2 sauces from all available)
     */
    @Min(value = 0, message = "El número máximo de salsas no puede ser negativo")
    @Column(name = "max_sauces")
    private Integer maxSauces;

    /**
     * Minimum number of sauces that must be selected for this item.
     * NULL or 0: No minimum required (the customer can skip sauces).
     * > 0: The customer must select at least this number of sauces/specialties.
     * Must be less than or equal to {@link #maxSauces} when maxSauces > 0.
     */
    @Min(value = 0, message = "El número mínimo de salsas no puede ser negativo")
    @Column(name = "min_sauces")
    private Integer minSauces;

    /**
     * Maximum number of specialities that can be selected for this item.
     * Mirrors {@link #maxSauces} for speciality-type complements.
     * NULL or 0: No specialities allowed or unlimited.
     * > 0: Maximum number of different specialities the customer can choose.
     */
    @Min(value = 0, message = "El número máximo de especialidades no puede ser negativo")
    @Column(name = "max_specialities")
    private Integer maxSpecialities;

    /**
     * Minimum number of specialities that must be selected for this item.
     * Mirrors {@link #minSauces} for speciality-type complements.
     * Must be less than or equal to {@link #maxSpecialities} when maxSpecialities > 0.
     */
    @Min(value = 0, message = "El número mínimo de especialidades no puede ser negativo")
    @Column(name = "min_specialities")
    private Integer minSpecialities;

    // ========== Availability Schedule ==========

    /**
     * Start time when this item becomes available for ordering.
     * This time applies to ALL available days for this item.
     * Example: If item is available Mon, Wed, Fri from 8:00 AM to 2:00 PM,
     * this field would be 08:00
     */
    @Column(name = "availability_start_time")
    private LocalTime availabilityStartTime;

    /**
     * End time when this item stops being available for ordering.
     * This time applies to ALL available days for this item.
     * Example: If item is available Mon, Wed, Fri from 8:00 AM to 2:00 PM,
     * this field would be 14:00
     */
    @Column(name = "availability_end_time")
    private LocalTime availabilityEndTime;

    /**
     * Indicates if this item has a custom availability schedule configured.
     * TRUE: Custom schedule is configured via availabilityDays
     * FALSE/NULL: No schedule restrictions, available whenever restaurant is open
     */
    @Column(name = "has_custom_schedule")
    @Builder.Default
    private Boolean hasCustomSchedule = false;

    // ========== Relationships ==========

    /**
     * Many-to-One relationship with Category
     * Each menu item belongs to a specific category
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private Category category;

    /**
     * One-to-Many relationship with ItemIngredient (Recipe)
     * List of ingredients required to prepare this menu item
     */
    @OneToMany(mappedBy = "itemMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemIngredient> ingredients = new ArrayList<>();

    /**
     * Many-to-Many relationship with Promotion
     * An item can have multiple promotions, and a promotion can apply to multiple items
     */
    @ManyToMany(mappedBy = "items", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Promotion> promotions = new ArrayList<>();

    /**
     * One-to-Many relationship with ItemMenuComplement
     * Defines which complements are available for this menu item
     */
    @OneToMany(mappedBy = "itemMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemMenuComplement> availableComplements = new ArrayList<>();

    /**
     * One-to-Many relationship with ItemMenuAvailability
     * Defines which days this menu item is available for ordering.
     * If empty, item is available all days.
     */
    @OneToMany(mappedBy = "itemMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemMenuAvailability> availabilityDays = new ArrayList<>();

    /**
     * One-to-Many relationship with ItemMenuComboItem
     * Defines which child menu items are included in this combo.
     * Only populated when isCombo = true.
     */
    @OneToMany(mappedBy = "comboMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemMenuComboItem> comboItems = new ArrayList<>();

    // ========== Size / Self-Reference ==========

    /**
     * Optional size label for this item when it acts as a size variant.
     * Examples: "Chica", "Mediana", "Grande", "Jumbo"
     * Only relevant when parentItem != null (this item is a child size) or
     * when it is the parent itself (e.g. parent's own label "Chica" as the base size).
     */
    @Size(max = 50, message = "El nombre del tamaño no puede exceder 50 caracteres")
    @Column(name = "size_name", length = 50)
    private String sizeName;

    /**
     * Self-referencing Many-to-One: if non-null, this item is a size variant of the parent.
     * E.g. "Pizza Grande" is a size variant of "Pizza".
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_item_id")
    private ItemMenu parentItem;

    /**
     * Self-referencing One-to-Many: the size variants that belong to this parent item.
     * Ordered by price ascending for display purposes.
     */
    @OneToMany(mappedBy = "parentItem", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemMenu> sizeItems = new ArrayList<>();

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Soft delete flag
     * FALSE: Item is active
     * TRUE: Item has been logically deleted
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /**
     * Lifecycle callback to set updatedAt before update operations
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Lifecycle callback to set createdAt before persist operations
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Business Logic Methods ==========

    /**
     * Add an ingredient to this menu item's recipe
     */
    public void addIngredient(ItemIngredient itemIngredient) {
        this.ingredients.add(itemIngredient);
        itemIngredient.setItemMenu(this);
    }

    /**
     * Remove an ingredient from this menu item's recipe
     */
    public void removeIngredient(ItemIngredient itemIngredient) {
        this.ingredients.remove(itemIngredient);
        itemIngredient.setItemMenu(null);
    }

    /**
     * Clear all ingredients from the recipe
     */
    public void clearIngredients() {
        this.ingredients.clear();
    }

    /**
     * Check if the item has a recipe defined (at least one ingredient)
     * Combo items don't have traditional recipes - they have child items instead.
     */
    public boolean hasRecipe() {
        if (Boolean.TRUE.equals(isCombo)) {
            return comboItems != null && !comboItems.isEmpty();
        }
        return ingredients != null && !ingredients.isEmpty();
    }

    /**
     * Get the total count of ingredients for this item.
     * For regular items: returns the count of direct ingredients.
     * For combo items: returns the sum of all ingredients from all child items.
     * For items without ingredients (requiresIngredients=false): returns 0.
     * @return Total ingredient count
     */
    public int getTotalIngredientsCount() {
        // Items without recipe have no ingredients
        if (!Boolean.TRUE.equals(requiresIngredients)) {
            return 0;
        }
        
        // Combo items: sum ingredients from all child items
        if (Boolean.TRUE.equals(isCombo)) {
            if (comboItems == null || comboItems.isEmpty()) {
                return 0;
            }
            return comboItems.stream()
                    .filter(ci -> ci.getChildMenu() != null)
                    .mapToInt(ci -> {
                        ItemMenu child = ci.getChildMenu();
                        // Recursively get ingredient count (handles nested combos)
                        return child.getTotalIngredientsCount() * ci.getQuantity();
                    })
                    .sum();
        }
        
        // Regular items: direct ingredient count
        return ingredients != null ? ingredients.size() : 0;
    }

    /**
     * Check if this is a combo item with child items defined
     */
    public boolean hasComboItems() {
        return Boolean.TRUE.equals(isCombo) && comboItems != null && !comboItems.isEmpty();
    }

    /**
     * Get the list of child items in this combo
     */
    public List<ItemMenuComboItem> getComboItemsList() {
        return comboItems != null ? comboItems : new ArrayList<>();
    }

    // ========== Size Helper Methods ==========

    /** Returns true if this item has at least one size variant. */
    public boolean hasSizeItems() {
        return sizeItems != null && !sizeItems.isEmpty();
    }

    /** Returns true if this item is itself a size variant (child) of another item. */
    public boolean isSizeItem() {
        return parentItem != null;
    }


    public BigDecimal calculateIngredientsCost() {
        if (!hasRecipe()) {
            return BigDecimal.ZERO;
        }

        return ingredients.stream()
                .map(ItemIngredient::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate profit margin percentage
     * Formula: ((price - cost) / price) * 100
     */
    public BigDecimal calculateProfitMarginPercentage() {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal cost = calculateIngredientsCost();
        BigDecimal profit = price.subtract(cost);
        
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(price, 2, RoundingMode.HALF_UP);
    }

    /**
     * Check if this item can be prepared based on current stock
     * @param quantity Number of items to prepare
     * @return true if there's enough stock for all ingredients
     */
    public boolean hasEnoughStock(int quantity) {
        // Combo items check stock of all child items
        if (Boolean.TRUE.equals(isCombo)) {
            if (comboItems == null || comboItems.isEmpty()) {
                return true;
            }
            return comboItems.stream()
                    .allMatch(comboItem -> comboItem.childHasEnoughStock(quantity));
        }

        if (!hasRecipe()) {
            return true; // Items without recipe are always available
        }

        return ingredients.stream()
                .allMatch(itemIngredient -> itemIngredient.hasEnoughStock(quantity));
    }

    /**
     * Calculate the maximum quantity of this item that can be prepared
     * based on current ingredient stock levels.
     * @return Maximum quantity available, or 9999 if no recipe (unlimited)
     */
    public int getMaxAvailableQuantity() {
        // Combo: max quantity = min of (child max qty / child combo qty) across all children
        if (Boolean.TRUE.equals(isCombo)) {
            if (comboItems == null || comboItems.isEmpty()) {
                return 9999;
            }
            int minAvailable = Integer.MAX_VALUE;
            for (ItemMenuComboItem comboItem : comboItems) {
                if (comboItem.getChildMenu() != null) {
                    int childMax = comboItem.getChildMenu().getMaxAvailableQuantity();
                    int combosFromChild = comboItem.getQuantity() > 0 ? childMax / comboItem.getQuantity() : 0;
                    minAvailable = Math.min(minAvailable, combosFromChild);
                }
            }
            return Math.max(0, minAvailable == Integer.MAX_VALUE ? 0 : minAvailable);
        }

        if (!hasRecipe() || ingredients.isEmpty()) {
            return 9999; // Items without recipe have virtually unlimited availability
        }

        int minAvailable = Integer.MAX_VALUE;
        
        for (ItemIngredient itemIngredient : ingredients) {
            if (itemIngredient.getIngredient() == null || 
                itemIngredient.getIngredient().getCurrentStock() == null ||
                itemIngredient.getQuantity() == null ||
                itemIngredient.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            // Calculate how many items we can make with this ingredient
            // Available = floor(currentStock / quantityPerItem)
            BigDecimal currentStock = itemIngredient.getIngredient().getCurrentStock();
            BigDecimal quantityPerItem = itemIngredient.getQuantity();
            
            int availableForThisIngredient = currentStock
                    .divide(quantityPerItem, 0, RoundingMode.FLOOR)
                    .intValue();
            
            minAvailable = Math.min(minAvailable, availableForThisIngredient);
        }
        
        // Return available quantity (no artificial cap, only ensure minimum of 0)
        return Math.max(0, minAvailable == Integer.MAX_VALUE ? 0 : minAvailable);
    }

    /**
     * Update availability based on current stock
     * Should be called after stock changes
     */
    public void updateAvailability() {
        this.available = hasEnoughStock(1);
    }

    // ========== Availability Schedule Methods ==========

    /**
     * Check if this item is available on a specific day of the week.
     * If hasCustomSchedule is false or no availability days are configured, item is available all days.
     * @param day The day to check
     * @return true if available on that day
     */
    public boolean isAvailableOnDay(DayOfWeek day) {
        // If no custom schedule configured, available all days (restaurant hours validation is separate)
        if (hasCustomSchedule == null || !hasCustomSchedule) {
            return true;
        }
        if (availabilityDays == null || availabilityDays.isEmpty()) {
            return true; // No restrictions = available all days
        }
        return availabilityDays.stream()
                .anyMatch(a -> a.getDayOfWeek() == day);
    }

    /**
     * Check if this item is available at a specific time.
     * If hasCustomSchedule is false or no time restrictions are configured, item is available all hours.
     * @param time The time to check
     * @return true if available at that time
     */
    public boolean isAvailableAtTime(LocalTime time) {
        // If no custom schedule configured, available all hours (restaurant hours validation is separate)
        if (hasCustomSchedule == null || !hasCustomSchedule) {
            return true;
        }
        // If no time restrictions, available all hours
        if (availabilityStartTime == null && availabilityEndTime == null) {
            return true;
        }
        // If only start time, available from start until end of day
        if (availabilityEndTime == null) {
            return !time.isBefore(availabilityStartTime);
        }
        // If only end time, available from start of day until end
        if (availabilityStartTime == null) {
            return !time.isAfter(availabilityEndTime);
        }
        // Both times configured
        return !time.isBefore(availabilityStartTime) && !time.isAfter(availabilityEndTime);
    }

    /**
     * Check if this item is available now (current day and time).
     * Combines day and time availability checks.
     * @return true if available right now
     */
    public boolean isAvailableNow() {
        // If no custom schedule configured, always available (restaurant hours validation is separate)
        if (hasCustomSchedule == null || !hasCustomSchedule) {
            return true;
        }
        LocalDateTime now = CompanyLocalTime.now();
        java.time.DayOfWeek javaDayOfWeek = now.getDayOfWeek();
        DayOfWeek customDay = DayOfWeek.valueOf(javaDayOfWeek.name());
        LocalTime currentTime = now.toLocalTime();
        
        return isAvailableOnDay(customDay) && isAvailableAtTime(currentTime);
    }

    /**
     * Check if this item is available at a specific day and time.
     * @param day The day to check
     * @param time The time to check
     * @return true if available at that day and time
     */
    public boolean isAvailableAt(DayOfWeek day, LocalTime time) {
        return isAvailableOnDay(day) && isAvailableAtTime(time);
    }

    /**
     * Get all days when this item is available
     * @return Set of available days, or all days if no restrictions
     */
    public Set<DayOfWeek> getAvailableDays() {
        if (availabilityDays == null || availabilityDays.isEmpty()) {
            return Set.of(DayOfWeek.values()); // All days
        }
        Set<DayOfWeek> days = new HashSet<>();
        availabilityDays.forEach(a -> days.add(a.getDayOfWeek()));
        return days;
    }

    /**
     * Check if this item has time restrictions
     * @return true if either start or end time is configured
     */
    public boolean hasTimeRestrictions() {
        return availabilityStartTime != null || availabilityEndTime != null;
    }

    /**
     * Check if this item has day restrictions
     * @return true if specific days are configured
     */
    public boolean hasDayRestrictions() {
        return availabilityDays != null && !availabilityDays.isEmpty();
    }

    /**
     * Get a human-readable description of the availability schedule
     * Includes per-day time ranges for better user information
     * @return Description of when this item is available
     */
    public String getAvailabilityDescription() {
        if (!hasDayRestrictions()) {
            return "Todos los días";
        }
        
        List<ItemMenuAvailability> sorted = availabilityDays.stream()
                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .collect(java.util.stream.Collectors.toList());
        
        List<String> dayDescriptions = sorted.stream()
                .map(a -> {
                    String dayName = a.getDayOfWeek().getDisplayName();
                    if (a.getStartTime() != null && a.getEndTime() != null) {
                        return dayName + " (" + a.getStartTime().toString() + " - " + a.getEndTime().toString() + ")";
                    }
                    return dayName;
                })
                .collect(java.util.stream.Collectors.toList());
        
        return String.join(", ", dayDescriptions);
    }

    /**
     * Get the schedule availability status for the current day and time.
     * Used by Thymeleaf templates to show visual indicators on menu item cards.
     * @return "available" if no schedule restrictions or currently within schedule,
     *         "day_unavailable" if today is not a configured available day,
     *         "time_unavailable" if today is available but outside the configured time range
     */
    public String getScheduleStatus() {
        if (hasCustomSchedule == null || !hasCustomSchedule) {
            return "available";
        }
        if (availabilityDays == null || availabilityDays.isEmpty()) {
            return "available";
        }
        
        LocalDateTime now = CompanyLocalTime.now();
        java.time.DayOfWeek javaDow = now.getDayOfWeek();
        DayOfWeek todayDay = DayOfWeek.valueOf(javaDow.name());
        LocalTime currentTime = now.toLocalTime();
        
        // Check if today is a configured day
        Optional<ItemMenuAvailability> todayAvail = availabilityDays.stream()
                .filter(a -> a.getDayOfWeek() == todayDay)
                .findFirst();
        
        if (todayAvail.isEmpty()) {
            return "day_unavailable";
        }
        
        // Today is available, check time range
        ItemMenuAvailability avail = todayAvail.get();
        if (avail.getStartTime() != null && avail.getEndTime() != null) {
            if (currentTime.isBefore(avail.getStartTime()) || currentTime.isAfter(avail.getEndTime())) {
                return "time_unavailable";
            }
        }
        
        return "available";
    }

    /**
     * Check if this item is currently available based on its schedule.
     * Convenience method that checks if scheduleStatus is "available".
     * @return true if the item is currently within its schedule
     */
    public boolean isScheduleAvailable() {
        return "available".equals(getScheduleStatus());
    }

    /**
     * Get a human-readable description of schedule info for the current day.
     * Used in SweetAlert error messages to inform the user.
     * @return Schedule information message
     */
    public String getScheduleInfo() {
        if (hasCustomSchedule == null || !hasCustomSchedule) {
            return "";
        }
        if (availabilityDays == null || availabilityDays.isEmpty()) {
            return "";
        }
        
        LocalDateTime now = CompanyLocalTime.now();
        java.time.DayOfWeek javaDow = now.getDayOfWeek();
        DayOfWeek todayDay = DayOfWeek.valueOf(javaDow.name());
        String todayName = todayDay.getDisplayName();
        
        Optional<ItemMenuAvailability> todayAvail = availabilityDays.stream()
                .filter(a -> a.getDayOfWeek() == todayDay)
                .findFirst();
        
        if (todayAvail.isPresent()) {
            ItemMenuAvailability avail = todayAvail.get();
            if (avail.getStartTime() != null && avail.getEndTime() != null) {
                return "Hoy (" + todayName + ") disponible de " + avail.getStartTime() + " a " + avail.getEndTime() + ".";
            }
            return "Disponible hoy (" + todayName + ").";
        }
        
        // Not available today
        return "No disponible hoy (" + todayName + "). Disponible: " + getAvailabilityDescription();
    }

    /**
     * Custom setter for requiresPreparation with logging
     * This helps debug form binding issues
     */
    public void setRequiresPreparation(Boolean requiresPreparation) {
        System.out.println("🔍🔍🔍 ItemMenu.setRequiresPreparation() called with value: " + requiresPreparation);
        System.out.println("🔍🔍🔍 Stack trace: " + Thread.currentThread().getStackTrace()[2]);
        this.requiresPreparation = requiresPreparation;
    }

    /**
     * Get formatted price with currency symbol
     */
    public String getFormattedPrice() {
        if (price == null) {
            return "$0.00";
        }
        return String.format("$%.2f", price);
    }

    /**
     * Get all active promotions for this item
     * @return List of promotions that are currently valid
     */
    public List<Promotion> getActivePromotions() {
        if (promotions == null || promotions.isEmpty()) {
            return new ArrayList<>();
        }
        
        return promotions.stream()
            .filter(Promotion::isValidNow)
            .sorted(Comparator.comparing(Promotion::getName))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get the best promotion for this item (highest savings)
     * @return The best promotion, or null if none available
     */
    public Promotion getBestPromotion() {
        List<Promotion> activePromotions = getActivePromotions();
        if (activePromotions.isEmpty()) {
            return null;
        }

        // Find promotion with maximum discount
        return activePromotions.stream()
            .max((p1, p2) -> {
                BigDecimal savings1 = calculateSavings(p1, 1);
                BigDecimal savings2 = calculateSavings(p2, 1);
                return savings1.compareTo(savings2);
            })
            .orElse(activePromotions.get(0)); // Fallback to first (highest priority)
    }

    /**
     * Calculate promotional price for this item with a specific promotion
     * @param promotion The promotion to apply
     * @param quantity The quantity being purchased
     * @return The discounted price (total for all items)
     */
    public BigDecimal calculatePromotionalPrice(Promotion promotion, int quantity) {
        if (promotion == null || price == null) {
            return price != null ? price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        }
        
        return promotion.calculateDiscountedPrice(price, quantity);
    }

    /**
     * Calculate savings from a promotion
     * @param promotion The promotion to apply
     * @param quantity The quantity being purchased
     * @return Amount saved
     */
    public BigDecimal calculateSavings(Promotion promotion, int quantity) {
        if (promotion == null || price == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal originalTotal = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal promotionalTotal = calculatePromotionalPrice(promotion, quantity);
        
        return originalTotal.subtract(promotionalTotal);
    }

    /**
     * Check if this item has any active promotions
     */
    public boolean hasActivePromotions() {
        return !getActivePromotions().isEmpty();
    }
}
