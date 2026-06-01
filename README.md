# TidyTalk

An Android file-cleaning app with a bring-your-own-AI workflow. TidyTalk builds a
prompt describing the files on your device; you paste it into any chat AI
(ChatGPT, Claude, etc.) and paste the answer back. TidyTalk then cleans up based
on the AI's suggestions — no API key, no cost.

## Status

Storage overview is in place: a device-storage summary plus per-category sizes,
a file browser that drills into directories sorted by size, and manual
checkbox-based deletion. The AI-driven cleaning flow is next. Requires the
all-files access permission (`MANAGE_EXTERNAL_STORAGE`).

## Build

```sh
./gradlew assembleDebug
```

Requires JDK 21 and an Android SDK (set `sdk.dir` in `local.properties` or the
`ANDROID_HOME` environment variable).

## Privacy

TidyTalk has no internet permission and collects no data. See the
[Privacy Policy](https://hogelog.github.io/tidytalk/privacy.html).

## License

MIT
