# CNNT — builds para download

## Última versão

- **Arquivo:** `cnnt-v0.2.1-beta-persistence.apk`
- **Versão:** 0.2.1-beta (versionCode 1)
- **Tipo:** debug (instalação direta no Android)

### Instalar no celular

1. Copie o `.apk` para o telefone (Downloads, cabo USB, etc.).
2. Abra o arquivo e permita “Instalar apps desconhecidos” se o sistema pedir.
3. Se já tiver uma versão antiga com o mesmo `applicationId`, desinstale ou instale por cima.

### Gerar de novo

```powershell
cd "C:\Users\Ryzen 5\Projects\cnnt-android"
.\gradlew.bat assembleDebug
```

APK gerado em: `app\build\outputs\apk\debug\app-debug.apk`

### Backup do código

O repositório GitHub: https://github.com/gbielserpa/cnnt-android

Faça `git push` após mudanças importantes para não depender só do APK local.
