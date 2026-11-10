plugins { kotlin("jvm"); application }
repositories { mavenCentral() }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.json:json:20250517")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
application { mainClass.set("io.chamamothanmal.client.SmokeMainKt") }
tasks.withType<Test>().configureEach { useJUnitPlatform() }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
dependencyLocking { lockAllConfigurations() }

// Desktop-only runner must not be compiled into the Android application.
kotlin.sourceSets.named("main") { kotlin.srcDir("src/cli/kotlin") }
