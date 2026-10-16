# sentencepiece-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fsentencepiece-kotlin-blue.svg)](https://github.com/KotlinMania/sentencepiece-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/sentencepiece-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/sentencepiece-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/sentencepiece-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/sentencepiece-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`danieldk/sentencepiece`](https://github.com/danieldk/sentencepiece) (crate `sentencepiece`).

**Original Project:** This port is based on [`danieldk/sentencepiece`](https://github.com/danieldk/sentencepiece). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `danieldk/sentencepiece`

> The text below is reproduced from [`https://github.com/danieldk/sentencepiece`](https://github.com/danieldk/sentencepiece). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs where appropriate so they continue to resolve from this repository.

# sentencepiece

This Rust crate is a binding for the
[sentencepiece](https://github.com/google/sentencepiece) unsupervised
text tokenizer. The [crate
documentation](https://docs.rs/sentencepiece/) is available
online.

## `libsentencepiece` dependency

This crate depends on the `sentencepiece` C++ library. By default,
this dependency is treated as follows:

* If `sentencepiece` could be found with `pkg-config`, the crate will
  link against the library found through `pkg-config`. **Warning:**
  dynamic linking only works correctly with sentencepiece 0.1.95
  or later, due to
  [a bug in earlier versions](https://github.com/google/sentencepiece/issues/579).
* Otherwise, the crate's build script will do a static build of the
  `sentencepiece` library. This requires that `cmake` is available.

If you wish to override this behavior, the `sentencepiece-sys` crate
offers two features:

* `system`: always attempt to link to the `sentencepiece` library
  found with `pkg-config`.
* `static`: always do a static build of the `sentencepiece` library
  and link against that.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:sentencepiece-kotlin:0.1.0")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same license terms as the upstream [`danieldk/sentencepiece`](https://github.com/danieldk/sentencepiece). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the sentencepiece authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`danieldk/sentencepiece`](https://github.com/danieldk/sentencepiece) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
