# Multi-Tenant Implementation Context

## Fecha de inicio: 2026-02-16

## Decisiones de Diseño Confirmadas

### 1. Programador Global
- El usuario PROGRAMMER NO tiene company_id
- Gestiona todas las empresas desde su dashboard
- El único usuario que existe "fuera" de las empresas

### 2. Clientes Globales
- Customer NO tiene company_id
- Un cliente puede loguearse en cualquier empresa con la misma cuenta
- Las órdenes, direcciones y reviews del cliente SÍ tienen company_id
- El cliente ve solo sus datos correspondientes a la empresa donde está logueado

### 3. Identificación por Slug/Dominio
- Formato: `{slug}.misistema.com` o dominio personalizado
- Para desarrollo: `localhost` se mapeará a una empresa de prueba

---

## Entidades y su relación con Company

### Entidades GLOBALES (sin company_id):
| Entidad | Razón |
|---------|-------|
| `Role` | Roles son globales del sistema |
| `Customer` | Clientes pueden acceder a múltiples empresas |
| `Employee` (PROGRAMMER) | Solo el programador es global |
| `SystemError` | Errores globales del sistema |

### Entidades con company_id DIRECTO:
| Entidad | Relación |
|---------|----------|
| `Company` | Es la entidad principal |
| `SystemConfiguration` | OneToOne con Company |
| `SystemLicense` | OneToOne con Company |
| `BackupConfiguration` | OneToOne con Company |
| `Employee` (no PROGRAMMER) | ManyToOne con Company |
| `Category` | ManyToOne con Company |
| `ItemMenu` | ManyToOne con Company |
| `Complement` | ManyToOne con Company |
| `Ingredient` | ManyToOne con Company |
| `IngredientCategory` | ManyToOne con Company |
| `Order` | ManyToOne con Company |
| `RestaurantTable` | ManyToOne con Company |
| `Reservation` | ManyToOne con Company |
| `Shift` | ManyToOne con Company |
| `Supplier` | ManyToOne con Company |
| `Promotion` | ManyToOne con Company |
| `Review` | ManyToOne con Company |
| `CustomerAddress` | ManyToOne con Company |
| `DailyOrderCounter` | Clave compuesta con company_id |

### Entidades que HEREDAN company de su padre:
| Entidad | Hereda de |
|---------|-----------|
| `OrderDetail` | Order.company |
| `OrderDetailComplement` | OrderDetail |
| `ItemIngredient` | ItemMenu.company |
| `ItemMenuComplement` | ItemMenu |
| `ItemMenuAvailability` | ItemMenu |
| `ItemMenuComboItem` | ItemMenu |
| `ComplementIngredient` | Complement |
| `BusinessHours` | SystemConfiguration |
| `SocialNetwork` | SystemConfiguration |
| `EmailVerificationToken` | Customer (sin company) |
| `PasswordResetToken` | Customer (sin company) |
| `EmployeeMonthlyStats` | Employee.company |
| `EmployeeShiftHistory` | Employee/Shift |
| `IngredientStockHistory` | Ingredient |
| `LicenseEvent` | SystemLicense |

---

## Progreso de Implementación

### Fase 1: Infraestructura Base
- [x] Crear entidad Company
- [x] Crear CompanyRepository
- [x] Crear CompanyService
- [x] Crear CompanyContext (ThreadLocal)
- [x] Crear CompanyContextFilter
- [x] Modificar SecurityConfig para agregar el filtro
- [x] Agregar app.base-domain a application.properties
- [ ] Crear CompanyInitializer (empresa localhost para dev)

### Fase 2: Modificar Entidades
- [ ] Agregar company a SystemConfiguration (OneToOne)
- [ ] Agregar company a SystemLicense (OneToOne)
- [ ] Agregar company a BackupConfiguration (OneToOne)
- [ ] Agregar company a Employee
- [ ] Agregar company a Category
- [ ] Agregar company a ItemMenu
- [ ] Agregar company a Complement
- [ ] Agregar company a Ingredient
- [ ] Agregar company a IngredientCategory
- [ ] Agregar company a Order
- [ ] Agregar company a RestaurantTable
- [ ] Agregar company a Reservation
- [ ] Agregar company a Shift
- [ ] Agregar company a Supplier
- [ ] Agregar company a Promotion
- [ ] Agregar company a Review
- [ ] Agregar company a CustomerAddress
- [ ] Modificar DailyOrderCounter (clave compuesta)

### Fase 3: Modificar Repositorios
- [ ] Agregar métodos con filtro por companyId donde sea necesario
- [ ] Crear CompanyRepository

### Fase 4: Modificar Servicios
- [ ] Modificar servicios para usar CompanyContext
- [ ] Modificar EmailService para obtener datos de Company
- [ ] Modificar LicenseService para trabajar por empresa
- [ ] Modificar SystemConfigurationService

### Fase 5: Modificar Autenticación
- [ ] Modificar CustomUserDetailsService
- [ ] Registrar CompanyContextFilter
- [ ] Modificar handlers de login/logout

### Fase 6: Modificar Controladores
- [ ] Modificar HomeController
- [ ] Modificar ClientAuthController
- [ ] Agregar gestión de Companies a ProgrammerController

### Fase 7: Modificar Inicializadores
- [ ] Modificar DefaultEmployeeInitializer
- [ ] Modificar LicenseInitializer
- [ ] Crear CompanyInitializer

### Fase 8: Vistas
- [ ] Crear vistas para gestión de Companies (programmer)

---

## Notas Importantes

1. **NO modificar lógica de validaciones existentes**
2. **NO cambiar funcionalidad actual**
3. **Solo agregar filtrado por company donde sea necesario**
4. **Probar cada cambio antes de continuar**

---

## Archivos Creados/Modificados

### Creados:
- (se irán agregando conforme se creen)

### Modificados:
- (se irán agregando conforme se modifiquen)

---

## Última Actualización: 2026-02-16
Estado actual: Iniciando Fase 1
