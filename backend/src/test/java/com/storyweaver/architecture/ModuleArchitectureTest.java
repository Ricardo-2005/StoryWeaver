package com.storyweaver.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.storyweaver")
class ModuleArchitectureTest {

    private static final String[] BUSINESS_MODULES = {
        "com.storyweaver.auth..",
        "com.storyweaver.project..",
        "com.storyweaver.canon..",
        "com.storyweaver.outline..",
        "com.storyweaver.chapter..",
        "com.storyweaver.character..",
        "com.storyweaver.worldbook..",
        "com.storyweaver.memory..",
        "com.storyweaver.skill..",
        "com.storyweaver.workflow..",
        "com.storyweaver.llm..",
        "com.storyweaver.review..",
        "com.storyweaver.usage..",
        "com.storyweaver.mcp.."
    };

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule mcp_must_not_access_repositories = noClasses()
            .that()
            .resideInAPackage("com.storyweaver.mcp..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule only_llm_may_access_deepseek = noClasses()
            .that()
            .resideOutsideOfPackage("com.storyweaver.llm..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.ai.deepseek..", "com.storyweaver.llm.deepseek..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule shared_must_not_access_business_modules = noClasses()
            .that()
            .resideInAPackage("com.storyweaver.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(BUSINESS_MODULES)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modules_must_be_free_of_cycles =
            slices().matching("com.storyweaver.(*)..").should().beFreeOfCycles().allowEmptyShould(true);
}
