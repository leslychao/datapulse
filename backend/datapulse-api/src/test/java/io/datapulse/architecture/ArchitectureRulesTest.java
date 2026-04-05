package io.datapulse.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@DisplayName("Architecture Rules")
class ArchitectureRulesTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("io.datapulse");
  }

  @Nested
  @DisplayName("Package conventions")
  class PackageConventions {

    @Test
    @DisplayName("RestController classes should reside in api package")
    void controllers_should_reside_in_api_package() {
      classes()
          .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .should().resideInAPackage("..api..")
          .because("all REST controllers must live in the api/ package")
          .check(allClasses);
    }

    @Test
    @DisplayName("Scheduled methods should not be in domain classes")
    void schedulers_should_not_reside_in_domain() {
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
          .because("scheduled tasks should live in scheduling/ package, not domain/")
          .check(allClasses);
    }
  }

  @Nested
  @DisplayName("Naming conventions")
  class NamingConventions {

    @Test
    @DisplayName("controllers should have Controller suffix")
    void controllers_should_have_controller_suffix() {
      classes()
          .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
          .should().haveSimpleNameEndingWith("Controller")
          .check(allClasses);
    }
  }

  @Nested
  @DisplayName("Dependency injection")
  class DependencyInjection {

    @Test
    @DisplayName("no field injection with @Autowired (except generated code)")
    void no_field_injection() {
      noFields()
          .that().areDeclaredInClassesThat().resideInAPackage("io.datapulse..")
          .and().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("MapperImpl")
          .and().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("Mapper")
          .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
          .because("use constructor injection via @RequiredArgsConstructor instead of @Autowired")
          .check(allClasses);
    }
  }

  @Nested
  @DisplayName("Adapter conventions")
  class AdapterConventions {

    @Test
    @DisplayName("adapter implementations should not reside in domain package")
    void adapters_should_not_reside_in_domain() {
      noClasses()
          .that().haveSimpleNameEndingWith("Adapter")
          .and().areNotInterfaces()
          .should().resideInAPackage("..domain..")
          .because("adapter implementations belong in adapter/ package; interfaces (ports) are OK in domain/")
          .check(allClasses);
    }
  }
}
