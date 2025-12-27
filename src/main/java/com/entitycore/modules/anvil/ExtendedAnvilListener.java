Welcome to Gradle 9.2.1!

Here are the highlights of this release:
 - Windows ARM support
 - Improved publishing APIs
 - Better guidance for dependency verification failures

For more details see https://docs.gradle.org/9.2.1/release-notes.html

Starting a Gradle Daemon (subsequent builds will be faster)

> Task :compileJava
/home/runner/work/EntityCore/EntityCore/src/main/java/com/entitycore/modules/anvil/ExtendedAnvilListener.java:36: error: expression type AnvilInventory is a subtype of pattern type AnvilInventory
        if (!(event.getInventory() instanceof AnvilInventory inv)) return;
                                   ^
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
1 error

> Task :compileJava FAILED

[Incubating] Problems report is available at: file:///home/runner/work/EntityCore/EntityCore/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':compileJava'.
1 actionable task: 1 executed
> Compilation failed; see the compiler output below.
  /home/runner/work/EntityCore/EntityCore/src/main/java/com/entitycore/modules/anvil/ExtendedAnvilListener.java:36: error: expression type AnvilInventory is a subtype of pattern type AnvilInventory
          if (!(event.getInventory() instanceof AnvilInventory inv)) return;
                                     ^
  1 error

* Try:
> Check your code and dependencies to fix the compilation error(s)
> Run with --scan to generate a Build Scan (powered by Develocity).

BUILD FAILED in 46s
Error: Process completed with exit code 1
