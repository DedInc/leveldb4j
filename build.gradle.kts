plugins {
    `java-library`
    `maven-publish`
}

group = "com.github.dedinc.leveldb4j"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.dedinc.leveldb4j"
            artifactId = "leveldb4j"
            version = "0.1.0"

            from(components["java"])

            // Configure POM for proper transitive dependency resolution
            pom {
                name.set("LevelDB4j")
                description.set("A pure Java library for reading LevelDB databases")
                url.set("https://github.com/dedinc/leveldb4j")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("dedinc")
                        name.set("DedInc")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/dedinc/leveldb4j.git")
                    developerConnection.set("scm:git:ssh://github.com:dedinc/leveldb4j.git")
                    url.set("https://github.com/dedinc/leveldb4j")
                }
            }
        }
    }
}
