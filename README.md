# Train Assistant

**Train Assistant** to aplikacja do wyszukiwania stacji i połączeń kolejowych. Można z niej korzystać w przeglądarce, a ten sam interfejs jest wykorzystywany w aplikacji na iOS.

**Wersja internetowa:** [train.macieserafin.pl](https://train.macieserafin.pl)

## Jak korzystać z aplikacji?

Otwórz [Train Assistant](https://train.macieserafin.pl) w przeglądarce. Nie trzeba niczego instalować.

Repozytorium zawiera również projekt iOS, który można samodzielnie zbudować i uruchomić na iPhonie lub w symulatorze za pomocą Xcode.

## Jak zbudowany jest projekt?

Wersja WWW i aplikacja iOS korzystają z **jednego interfejsu w HTML, CSS i JavaScript**, znajdującego się w katalogu `frontend/`. Do przygotowania aplikacji iOS używany jest **Capacitor 8**. Za obsługę żądań API odpowiada **backend Spring Boot**, działający na VPS.

Najważniejsze katalogi i pliki:

- `frontend/index.html`, `frontend/js/`, `frontend/css/` — kod interfejsu.
- `frontend/ios/` — projekt aplikacji iOS otwierany w Xcode.
- `backend/` — backend Spring Boot.
- `ops/DEPLOYMENT.md` — szczegóły wdrożenia i działania środowiska produkcyjnego.

Katalog `frontend/dist/` oraz zasoby w `frontend/ios/App/App/public/` są generowane automatycznie. Zmiany w interfejsie należy wprowadzać w plikach źródłowych, a nie w ich wygenerowanych kopiach.

## Uruchomienie lokalne

### Wymagania

- **Node.js 22+** i npm — do pracy nad frontendem.
- **Java 21+** — do uruchomienia backendu.
- **Mac z Xcode 26+** oraz zainstalowaną obsługą iOS — tylko do budowania aplikacji iOS. Same Xcode Command Line Tools nie wystarczą.

Wersję WWW można rozwijać także na Windowsie.

### Start aplikacji

Uruchom backend w pierwszym terminalu, z katalogu `backend/`:

```sh
./mvnw spring-boot:run
```

W drugim terminalu, z katalogu `frontend/`, uruchom stronę:

```sh
npm ci
npm run dev
```

Aplikacja będzie dostępna pod adresem **http://localhost:5173**.

Podczas pracy lokalnej serwer frontendu przekazuje żądania `/api/*` do `http://127.0.0.1:8080`. Dzięki temu przeglądarka nie wymaga dodatkowej konfiguracji CORS. Tryb `dev` zawsze używa tego lokalnego proxy.

## Konfiguracja API

Dla aplikacji iOS oraz wersji WWW korzystającej z API pod osobną domeną należy skonfigurować adres backendu. Skopiuj `frontend/.env.example` do `frontend/.env` i ustaw:

```dotenv
API_BASE_URL=https://api.twojadomena.pl
```

Podaj **sam adres serwera, bez `/api`**. Aplikacja sama tworzy adresy żądań, np. `/api/stations` i `/api/connections`.

Ważne informacje:

- Aplikacja iOS wymaga adresu API z poprawnym certyfikatem **HTTPS**. Brak adresu lub użycie HTTP zatrzyma budowanie iOS.
- Wersja WWW zbudowana bez `API_BASE_URL` korzysta z `/api` pod tą samą domeną co strona. W takim przypadku serwer WWW musi przekazywać żądania do backendu.
- Zmiana adresu API wymaga ponownego zbudowania WWW lub synchronizacji projektu iOS.
- Adres API jest publiczną konfiguracją wbudowywaną w aplikację. **Nie należy umieszczać w nim ani w kodzie frontendu kluczy dostępowych.** Klucz PKP pozostaje wyłącznie na backendzie. Pliki `.env` nie są kopiowane do gotowej aplikacji przez skrypt budowania.

Jeśli WWW i API są dostępne pod różnymi domenami, backend musi zezwalać na odpowiedni origin. Przykładowa konfiguracja na VPS-ie:

```dotenv
APP_CORS_ALLOWED_ORIGINS=capacitor://localhost,https://twojadomena.pl
```

Backend domyślnie dopuszcza `capacitor://localhost`. Przy WWW i API pod tym samym adresem dodatkowy origin strony nie jest potrzebny. Reverse proxy (np. Nginx) powinno obsługiwać HTTPS oraz przekazywać `/api/*` do Spring Boot pod `127.0.0.1:8080`, z zachowaniem nagłówków CORS i obsługą żądań `OPTIONS`.

## Uruchomienie aplikacji iOS

Na Macu, po ustawieniu `frontend/.env`, wykonaj w katalogu `frontend/`:

```sh
npm ci
npm run ios:sync
npm run ios:open
```

Polecenie `ios:sync` buduje interfejs i kopiuje go do projektu iOS. Następnie w Xcode:

1. Dodaj konto Apple w **Settings → Accounts**.
2. Otwórz **App → Signing & Capabilities**, włącz **Automatically manage signing** i wybierz swój **Team**.
3. Wybierz symulator lub podłączonego iPhone'a i kliknij **Run**.
4. Na fizycznym iPhonie, w razie potrzeby, zatwierdź zaufanie do komputera i włącz **Ustawienia → Prywatność i ochrona → Tryb deweloperski**.

Po zmianach w HTML, CSS lub JavaScript ponownie uruchom `npm run ios:sync`, a następnie **Run** w Xcode. Alternatywnie użyj `npm run ios:run`, aby wybrać urządzenie w terminalu.

Identyfikator aplikacji: `pl.macieserafin.trainassistant`. Nazwa: **Train Assistant**. Obie wartości są zdefiniowane w `frontend/capacitor.config.json`.

Polecenie `npm run ios:add` służy wyłącznie do odtworzenia usuniętego projektu iOS. **Nie uruchamiaj go, jeśli katalog `frontend/ios/` już istnieje.**

## Testy i wdrożenie

Testy frontendu (w `frontend/`):

```sh
npm test
```

Testy backendu (w `backend/`):

```sh
./mvnw test
```

Budowanie wersji WWW (w `frontend/`):

```sh
npm run build
```

Gotowe pliki trafiają do `frontend/dist/` i mogą zostać opublikowane na serwerze WWW.

W tym projekcie **push do gałęzi `main` automatycznie uruchamia testy, budowanie oraz wdrożenie WWW i backendu na VPS**. Szczegóły konfiguracji i procesu znajdują się w [dokumentacji wdrożenia](ops/DEPLOYMENT.md).

## Dokumentacja technologii

- [Capacitor — dokumentacja](https://capacitorjs.com/docs/getting-started)
- [Capacitor — iOS](https://capacitorjs.com/docs/ios)
