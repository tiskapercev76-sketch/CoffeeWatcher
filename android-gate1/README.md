# Гоняй лысого — Android Gate 1 smoke

Это временный нативный smoke-проект для проверки всей Android-цепочки сборки без большого игрового asset.

Параметры совпадают с Native TEST 01:
- package base: ru.elmarto.lysy
- debug applicationId: ru.elmarto.lysy.gate1
- minSdk 24
- compile/targetSdk 35
- Java 17
- portrait
- local WebView
- no INTERNET permission
- Android Back -> LysyGateApp.handleBack()
- onPause -> forcePause()/setPaused(true)
- onResume не снимает игровую паузу

После проверки APK временный app/src/main/assets/index.html будет заменён на принятый Gate 1 web asset.
