import org.gradle.jvm.toolchain.JavaLanguageVersion

object Versions {
    const val APP_CODE = 21
    const val APP_NAME = "1.0.20"

    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 29
    const val BUILD_TOOLS = "37.0.0"

    val JAVA = JavaLanguageVersion.of(17)
}