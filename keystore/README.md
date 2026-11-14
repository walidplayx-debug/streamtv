# keystore

`stv-public.jks` is a **public** signing key (store password, key password: `stvpublic`, alias: `stv`).
It exists so that every APK built from this repository is signed with the same key and can be
installed over the previous one without uninstalling first.

Because the key is public, it proves nothing about who built an APK. To sign with a private key
instead, add the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository
secrets; the workflow then uses that key automatically.
