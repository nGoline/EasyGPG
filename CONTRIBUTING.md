# Contributing to Easy GPG

Thanks for helping keep Easy GPG alive! This project welcomes issues, ideas, and
pull requests.

## Getting started

1. Fork the repository and clone your fork.
2. Make sure you can build it (see the [README](README.md#building)) — you need
   Android Studio and JDK 21.
3. Create a branch for your change: `git checkout -b my-fix`.

## Making changes

- Keep the existing code style: idiomatic Kotlin, matching the naming and
  structure already in `app/src/main/java/com/ngoline/easygpg/`.
- Keep pull requests focused — one logical change per PR is easier to review.
- If you add or change behavior, describe how you tested it in the PR.
- Do **not** commit machine-specific or sensitive files. `local.properties`,
  `.idea/`, keystores (`*.jks`, `*.keystore`), and build outputs (`*.apk`,
  `*.aab`) are already gitignored — please keep it that way.

## Reporting bugs & requesting features

Open a GitHub issue with:

- What you expected vs. what happened
- Steps to reproduce
- Device / Android version, and app version (`versionName` in
  `app/build.gradle.kts`)

## Security issues

Because this app handles cryptographic keys, **please report security
vulnerabilities privately** rather than in a public issue — email the maintainer
so a fix can be prepared before disclosure.

## License of contributions

By contributing, you agree that your contributions will be licensed under the
project's [GNU GPLv3](LICENSE).
