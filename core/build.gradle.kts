plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    // Test-only (ADR 0005): keeps spec/ and the fixtures in line with the schema.
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    // The schema test reads spec/ at the repository root.
    systemProperty("pie.specDir", rootProject.file("spec").absolutePath)
    inputs.dir(rootProject.file("spec"))
}

// CI runs `testDebugUnitTest` across all projects; a plain JVM module only has `test`.
tasks.register("testDebugUnitTest") {
    dependsOn(tasks.test)
}
