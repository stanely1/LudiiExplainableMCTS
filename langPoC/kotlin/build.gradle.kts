plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(files("lib/Ludii-1.3.14.jar"))
}

application {
    mainClass.set("main.RunLudiiKt")
}
