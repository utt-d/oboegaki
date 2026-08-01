# Project instructions

- `SPECIFICATION.md` is the product source of truth. User-visible Japanese must use 「やること」, never 「タスク」.
- Keep business rules in `shared/src/commonMain/kotlin/jp/oboegaki/core`; Compose UI must not decide ordering constraints.
- Preserve offline-first behavior. Do not add analytics, ads, accounts, or network synchronization.
- Destructive or state-changing actions must remain undoable where the specification requires it.
- New user-visible strings belong in `shared/src/commonMain/composeResources/values/strings.xml` when practical.
- Prefer typed results over thrown exceptions for user-correctable domain errors.
- Add or update common tests for ordering, defer, split, conversion, and theme validation rules.
- Run `./gradlew.bat :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug` before handing off changes.
- iOS code must remain in shared/ios source sets or `iosApp`; do not move domain rules into Swift.
