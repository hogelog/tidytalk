# TidyTalk

An Android file-cleaning app with a bring-your-own-AI workflow. TidyTalk builds a
prompt describing the files on your device; you paste it into any chat AI
(ChatGPT, Claude, etc.) and paste the answer back. TidyTalk then cleans up based
on the AI's suggestions — no API key, no cost.

## Status

Early scaffold: an empty Jetpack Compose app.

## Build

```sh
./gradlew assembleDebug
```

Requires JDK 21 and an Android SDK (set `sdk.dir` in `local.properties` or the
`ANDROID_HOME` environment variable).

## License

MIT
