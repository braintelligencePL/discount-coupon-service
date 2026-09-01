package com.example.coupons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the pragmatic layered architecture with an inbound / sealed-outbound
 * split of {@code infrastructure}:
 *
 * <ul>
 *   <li><b>Inbound</b> = {@code api} (controllers, DTOs) + {@code infrastructure.web}
 *       (problem+json advice, correlation-id filter, client-IP resolver) — the HTTP
 *       edge; no layer may depend on it.</li>
 *   <li><b>Infrastructure</b> = {@code infrastructure.persistence} +
 *       {@code infrastructure.geoip} — the outbound adapters; sealed, reachable only
 *       through the ports {@code application} owns.</li>
 *   <li><b>Application</b> — reachable only from Inbound and Infrastructure.</li>
 *   <li><b>Domain</b> — framework-free; reachable from everywhere, depends on nothing.</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.example.coupons",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    @ArchTest
    static final ArchRule the_domain_is_pure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..api..", "..application..", "..infrastructure..",
                            "org.springframework..", "jakarta.persistence..",
                            "com.fasterxml.jackson..", "com.github.benmanes.caffeine..",
                            "io.github.resilience4j..")
                    .as("the domain must not reference the outer layers, Spring, persistence, "
                            + "serialization, caching or resilience libraries");

    @ArchTest
    static final ArchRule respects_layering =
            layeredArchitecture().consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Inbound").definedBy("..api..", "..infrastructure.web..")
                    .layer("Infrastructure")
                        .definedBy("..infrastructure.persistence..", "..infrastructure.geoip..")
                    .whereLayer("Inbound").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Inbound", "Infrastructure")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Inbound", "Application", "Infrastructure");
}
