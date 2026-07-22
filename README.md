# Boot Patcher

Boot Patcher is an offline Android utility that replaces the kernel in a boot
image. It accepts a raw `Image` file or an AnyKernel3 ZIP containing `Image`,
then runs this pipeline:

1. `magiskboot unpack boot.img`
2. Replace the unpacked `kernel`
3. `magiskboot repack boot.img`
4. Save the generated image to the folder configured in Settings

The output name defaults to `new-boot.img`, remains editable in Settings, and
always uses the `.img` extension. The selected source files remain unchanged.
All intermediate files live in an app-specific cache directory and are removed
after each operation.

## Build

Prerequisites: Android SDK 37 and JDK 17.

```sh
./gradlew assembleUniversalDebug
```

The APK is generated under `app/build/outputs/apk/universal/debug/`.

Release builds are available for five ABI variants:

| Variant | Included ABI |
| --- | --- |
| `universal` | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| `arm64` | `arm64-v8a` |
| `arm32` | `armeabi-v7a` |
| `x86` | `x86` |
| `x86_64` | `x86_64` |

GitHub Actions builds all five optimized release APKs on pushes to `main`, pull
requests, and manual dispatches. A manual dispatch can optionally publish a
GitHub Release containing every APK and their SHA-256 hashes.

Release signing supports these repository secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

When signing secrets are absent, CI generates one temporary key for all five
APKs in that workflow run. Configure the secrets before publishing stable
updates so subsequent releases retain the same signing identity.

## Bundled magiskboot

The native binaries were extracted without modification from the official
[Magisk v30.7 APK](https://github.com/topjohnwu/Magisk/releases/tag/v30.7).
The downloaded APK SHA-256 was:

```text
e0d32d2123532860f97123d927b1bb86c4e08e6fd8a48bfc6b5bee0afae9ebd5
```

Included ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

Magisk is Copyright (C) 2016-2026 John Wu and contributors, licensed under
GPL-3.0. The exact corresponding source is available from the
[v30.7 source tag](https://github.com/topjohnwu/Magisk/tree/v30.7). A copy of
the license is included at `third_party/Magisk-LICENSE`.

## Limits

- The boot image must contain a kernel recognized by `magiskboot`.
- AnyKernel3 archives must contain a file whose basename is exactly `Image`.
- Kernel inputs are limited to 512 MiB.
- The output is unsigned. Device-specific AVB or flashing requirements still
  apply.
