package ch.aschwanden.mailtriage;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Erzwingt die hexagonale Struktur - wie im Smarthome-Projekt:
 * Domain kennt niemanden, Application kennt nur Domain und Ports,
 * Adapter kennen sich gegenseitig nicht.
 */
@AnalyzeClasses(packages = "ch.aschwanden.mailtriage",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "ch.aschwanden.mailtriage.domain..";
    private static final String APPLICATION = "ch.aschwanden.mailtriage.application..";
    private static final String PORT = "ch.aschwanden.mailtriage.port..";
    private static final String ADAPTER = "ch.aschwanden.mailtriage.adapter..";

    @ArchTest
    static final ArchRule domainIstFrameworkfrei = classes()
            .that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(DOMAIN, "java..");

    @ArchTest
    static final ArchRule portsKennenNurDomain = classes()
            .that().resideInAPackage(PORT)
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(PORT, DOMAIN, "java..");

    @ArchTest
    static final ArchRule applicationKenntKeineAdapter = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(ADAPTER);

    @ArchTest
    static final ArchRule adapterKennenSichNicht = noClasses()
            .that().resideInAPackage("ch.aschwanden.mailtriage.adapter.out.imap..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "ch.aschwanden.mailtriage.adapter.out.lmstudio..",
                    "ch.aschwanden.mailtriage.adapter.out.persistence..");
}
