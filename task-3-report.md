# Task 3 report

- Added a codegen-enabled regression for overloaded annotated `@Screen` composables.
- `SC11` now rejects annotated `@Screen` overload declarations with the function name and every affected route; repeated `@Screen` annotations on one declaration remain valid.
- Explicit `@EffectHandler` overloads for different routes remain covered by their existing positive regression.

Verification:

- `GRADLE_USER_HOME=/private/tmp/gezgin-gradle ./gradlew :gezgin-processor:test --tests 'dev.gezgin.processor.MviEntryCodegenTest.codegen-enabled overloaded Screen declarations fail with routes and function name' --tests 'dev.gezgin.processor.MviModelReaderTest.overloaded EffectHandler declarations for different routes are both retained' --tests 'dev.gezgin.processor.MviModelReaderTest.overloaded Screen declarations are rejected with routes and function name' --rerun-tasks`
- `GRADLE_USER_HOME=/private/tmp/gezgin-gradle ./gradlew :gezgin-processor:test --rerun-tasks`
