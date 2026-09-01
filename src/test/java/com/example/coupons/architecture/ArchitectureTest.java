package com.example.coupons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Which package may depend on which. Every arrow points toward the domain.
 *
 * <pre>
 *   api  +  infrastructure.web      the web classes: take requests, send responses
 *        |
 *        v
 *   application                     the business logic
 *        |
 *        v
 *   domain                          plain Java: the model and the rules
 *
 *   infrastructure.persistence      talks to the database
 *   infrastructure.geoip            talks to the geo-IP service
 *        Spring connects these; no other class imports them
 * </pre>
 *
 * The four rules below just enforce those arrows. If one fails, its
 * {@code because(...)} text says which arrow was broken.
 */
@AnalyzeClasses(
        packages = "com.example.coupons",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_on_nothing_else =
            classes().that().resideInAPackage("..domain..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..")
                    .because("the domain is plain Java -- it must not use Spring, the database, "
                            + "JSON, or anything from the other packages");

    @ArchTest
    static final ArchRule application_only_uses_the_domain =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                    .because("the business logic may use the domain and nothing else -- not the web "
                            + "classes, not the database or geo-IP classes");

    @ArchTest
    static final ArchRule nothing_uses_the_web_classes =
            noClasses().that().resideOutsideOfPackages("..api..", "..infrastructure.web..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure.web..")
                    .because("the web classes take requests and send responses -- the rest of the app "
                            + "must not call back into them");

    @ArchTest
    static final ArchRule nothing_uses_the_database_or_geoip_classes =
            noClasses().that().resideOutsideOfPackages(
                            "..infrastructure.persistence..", "..infrastructure.geoip..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure.persistence..", "..infrastructure.geoip..")
                    .because("the database and geo-IP classes are reached through interfaces in "
                            + "application -- no other class should import them directly");
}
