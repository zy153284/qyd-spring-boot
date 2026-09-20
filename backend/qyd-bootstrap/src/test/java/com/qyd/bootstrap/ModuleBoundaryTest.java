package com.qyd.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@AnalyzeClasses(packages="com.qyd",importOptions=ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {
    @Test
    void springModulithBoundariesAreValid() {
        ApplicationModules.of(QydApplication.class).verify();
    }

    @ArchTest
    static final com.tngtech.archunit.lang.ArchRule domainPackagesHaveNoCycles =
            SlicesRuleDefinition.slices().matching("com.qyd.(*)..")
                    .should().beFreeOfCycles();
}
