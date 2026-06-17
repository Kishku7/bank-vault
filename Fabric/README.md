# Bank Vault - Fabric (Minecraft 26.2 (pre-release))

**Fabric** loader builds of Bank Vault for the Minecraft 26.2 (pre-release) line. Client + server mod. Fabric only on this line (no Quilt - see below).

## Builds

| Version | Minecraft | Java | Mod ver | Shared common |
| --- | --- | --- | --- | --- |
| [`26.2/`](26.2) | 26.2 (pre-release) | 25 | 1.2.4 | - |

## Excluded / not built

- **Quilt** is not supported on the 26.x line - Quilt retired Quilted Fabric API at 26.1, so the Fabric API path Bank Vault uses on Fabric is no longer provided on Quilt for 26.x. (Quilt remains supported on the 1.20.x and 1.21.x branches.)

## Build

```
cd <version>
./gradlew build      # Windows: .\gradlew.bat build
```

Output: `build/libs/bank-vault-*.jar`. Part of the [`26.2` branch](../README.md).
