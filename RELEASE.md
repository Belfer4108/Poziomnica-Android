# Przygotowanie wydania

Release robimy dopiero po testach wszystkich modulow na prawdziwym telefonie.

Aktualne przygotowywane wydanie: `v1.1.0`.

## Kroki przed release

1. Sprawdz `TEST_PLAN.md`.
2. Zaktualizuj `versionCode` i `versionName` w `app/build.gradle.kts`.
3. Uruchom testy:

```powershell
.\gradlew.bat test
```

4. Zbuduj APK debug do ostatniego testu:

```powershell
.\gradlew.bat assembleDebug
```

5. Skonfiguruj podpis release.
6. Zbuduj release:

```powershell
.\gradlew.bat assembleRelease
```

7. Utworz tag Git:

```powershell
git tag v1.1.0
git push origin v1.1.0
```

8. Na GitHub utworz release i dodaj APK jako zalacznik.

## Docelowe repozytorium

`https://github.com/Belfer4108/Poziomnica-Android`

## Co dolaczyc do opisu release

- numer wersji,
- najwazniejsze funkcje,
- lista napraw,
- znane ograniczenia,
- informacja, ze dane sa lokalne i aplikacja nie wymaga konta.

## Znane ograniczenie lokalnego srodowiska testowego

Na tej maszynie `testDebugUnitTest` moze konczyc sie bledem uruchomienia procesu Gradle Test Executor:

`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`

To jest blad startu workera testowego Gradle, nie wynik nieudanej asercji testu. Przed publikacja trzeba sprawdzic testy ponownie w Android Studio albo w GitHub Actions.
