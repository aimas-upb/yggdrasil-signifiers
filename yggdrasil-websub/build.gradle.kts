plugins {
  java
  `java-library`
  checkstyle
  pmd
  alias(libs.plugins.spotbugs)
  jacoco
}

checkstyle {
  config = resources.text.fromFile("${rootProject.projectDir}/checkstyle.xml")
  toolVersion = libs.versions.checkstyle.get()
}

pmd {
  toolVersion = libs.versions.pmd.get()
  ruleSetConfig = resources.text.fromFile("${rootProject.projectDir}/pmd.xml")
}

spotbugs {
  toolVersion = libs.versions.spotbugs
}

jacoco {
  toolVersion = libs.versions.jacoco.get()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(23))
    vendor.set(JvmVendorSpec.ORACLE) // Optional: Specify vendor (e.g., Oracle, OpenJDK)
  }
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
  implementation(project(":yggdrasil-utils"))

  implementation(libs.log4j.core)
  implementation(libs.vertx.core)
  implementation(libs.vertx.web.client)

  implementation(libs.httpcomponents.core5)

  implementation(libs.guava)

  compileOnly(libs.spotbugs.annotations)
  pmd(libs.pmd.java)
  pmd(libs.pmd.ant)

  testImplementation(platform(libs.junit.platform))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.vertx.junit5)

  testCompileOnly(libs.spotbugs.annotations)
}

tasks {
  test {
    useJUnitPlatform()
    finalizedBy(jacocoTestReport)
  }

  spotbugsMain {
    reports.create("html") {
        required.set(true)
    }
  }
  spotbugsTest {
    reports.create("html") {
        required.set(true)
    }
  }
}

