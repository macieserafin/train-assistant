# Train Assistant — WWW i iOS

Produkcja: https://train.macieserafin.pl. Push do `main` automatycznie testuje,
buduje i wdraża WWW oraz backend na VPS. Szczegóły: [ops/DEPLOYMENT.md](ops/DEPLOYMENT.md).

Jeden frontend HTML/CSS/JavaScript w `frontend/` obsługuje stronę WWW i aplikację
iOS przez Capacitor 8. Edytuj `index.html`, `js/` i `css/`; katalog `dist/` oraz
kopie zasobów w projekcie iOS są generowane. Backend Spring Boot pozostaje na VPS-ie.

## Wymagania

- Node.js 22 lub nowszy i npm.
- Java 21 lub nowsza do backendu.
- Do budowania iOS: Mac, pełny Xcode 26+ i obsługa iOS zainstalowana w Xcode.
  Same Command Line Tools nie wystarczą. Kod WWW można rozwijać także na Windowsie.

## Uruchomienie lokalnej strony

W pierwszym terminalu, z katalogu `backend/`:

```sh
./mvnw spring-boot:run
```

W drugim terminalu, z katalogu `frontend/`:

```sh
npm ci
npm run dev
```

Otwórz http://localhost:5173. Serwer Node przekazuje `/api/*` do
http://127.0.0.1:8080; przeglądarka nie potrzebuje wtedy CORS.
Lokalny tryb `dev` zawsze korzysta z tego proxy.

## Adres backendu na VPS-ie

Skopiuj `frontend/.env.example` do `frontend/.env` i zmień przykład na swój adres:

```dotenv
API_BASE_URL=https://api.twojadomena.pl
```

Wpisz sam adres serwera, bez `/api`. Aplikacja sama dodaje `/api/stations` lub
`/api/connections`. Backend musi być dostępny przez HTTPS z poprawnym certyfikatem.
Adres jest publiczną konfiguracją dołączaną do aplikacji; klucz PKP zostaje
wyłącznie na backendzie. Skrypt budowania nie kopiuje plików `.env`.

Backend domyślnie dopuszcza origin aplikacji iOS `capacitor://localhost`.
Jeśli strona WWW i API mają różne domeny, ustaw na VPS-ie zmienną środowiskową:

```dotenv
APP_CORS_ALLOWED_ORIGINS=capacitor://localhost,https://twojadomena.pl
```

Przy stronie i API pod tym samym adresem dodatkowy origin WWW nie jest potrzebny.
Reverse proxy (np. Nginx) powinno obsługiwać HTTPS i przekazywać `/api/*` do
Spring Boot na `127.0.0.1:8080`, zachowując nagłówki CORS oraz żądania OPTIONS.

## Aplikacja iOS

Projekt jest w `frontend/ios/`. Z katalogu `frontend/`, po ustawieniu `.env`:

```sh
npm ci
npm run ios:sync
npm run ios:open
```

`ios:sync` buduje te same ekrany co WWW i kopiuje je do projektu iOS.
Brak adresu API lub adres bez HTTPS zatrzymuje budowanie iOS.
Identyfikator aplikacji to `pl.macieserafin.trainassistant`, a nazwa to
`Train Assistant` (konfiguracja w `capacitor.config.json`).

W Xcode:

1. Dodaj konto Apple w Settings → Accounts.
2. Wybierz projekt App, target App → Signing & Capabilities.
3. Włącz Automatically manage signing i wybierz swój Team.
4. Wybierz symulator lub podłączonego iPhone'a i kliknij Run.
5. Na fizycznym telefonie zatwierdź zaufanie do Maca i w razie potrzeby włącz
   Ustawienia → Prywatność i ochrona → Tryb deweloperski.

Po zmianach w HTML/CSS/JS wykonaj ponownie `npm run ios:sync` i Run w Xcode.
Możesz też użyć `npm run ios:run`, aby wybrać urządzenie z terminala.
Nie edytuj wygenerowanych plików w `ios/App/App/public/`.
Przy odtwarzaniu usuniętego projektu iOS służy do tego `npm run ios:add`;
nie uruchamiaj go, jeśli katalog `ios/` już istnieje.

## Wersja WWW do wdrożenia

```sh
npm run build
```

Opublikuj zawartość `frontend/dist/` na serwerze WWW. Build używa
`API_BASE_URL` z `.env` lub środowiska. Bez tej zmiennej korzysta z `/api`
pod adresem strony — wtedy skonfiguruj reverse proxy do backendu.
Zmiana adresu API wymaga ponownego budowania WWW lub synchronizacji iOS.

## Sprawdzenie zmian

W `frontend/`: `npm test`. W `backend/`: `./mvnw test`.

Dokumentacja: [Capacitor](https://capacitorjs.com/docs/getting-started),
[iOS](https://capacitorjs.com/docs/ios).

