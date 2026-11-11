plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)
}

// CI runs `testDebugUnitTest` across all projects; a plain JVM module only has `test`.
tasks.register("testDebugUnitTest") {
    dependsOn(tasks.test)
}
