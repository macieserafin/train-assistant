# Wdrożenie Train Assistant

Push do `main` uruchamia `.github/workflows/deploy.yml`. Obraz Dockera zawiera
wspólny frontend WWW oraz backend. Budowanie uruchamia testy Node i Maven;
nieudane testy zatrzymują wdrożenie. Pull request tylko buduje i testuje obraz.

Na VPS wysyłany jest obraz z tagiem SHA commita oraz pliki Compose i skrypt
wdrożeniowy. Każde wydanie ma katalog `~/train-assistant/releases/SHA`.
Kontener ma osobny projekt Compose `train-assistant` i port
`127.0.0.1:18080`. Nginx obsługuje HTTPS dla `train.macieserafin.pl` i przekazuje
tam stronę oraz `/api/*`. Inne projekty Compose pozostają niezależne.

`~/train-assistant/.env` zawiera klucz PKP i pozostaje wyłącznie na VPS-ie.
Nie jest częścią obrazu, artefaktów GitHub ani repozytorium. Wersja WWW używa
względnych adresów `/api`; aplikacja iOS powinna być budowana z
`API_BASE_URL=https://train.macieserafin.pl` w `frontend/.env`.

## Ustawienia GitHub

Secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS`.
Variable: `PUBLIC_URL=https://train.macieserafin.pl`.
Zadanie wdrożeniowe korzysta ze środowiska `production`.
Klucz wdrożeniowy jest osobny dla tego projektu; jego publiczny odpowiednik
w `authorized_keys` ma opcję `restrict`. Konto ma dostęp administracyjny przez
Docker, więc klucz musi pozostać sekretem GitHub Actions.

## Aktualizacje i cofanie wersji

Po pushu obserwuj GitHub → Actions. Workflow działa też ręcznie na `main`.
`ops/deploy.sh` blokuje równoległe wdrożenia, czeka na zdrowy kontener i sprawdza
lokalne `/healthz`. Przy błędzie próbuje przywrócić poprzednie wydanie.
Pierwsze wdrożenie nie ma wersji do przywrócenia. Weryfikacja publicznego HTTPS
jest osobnym krokiem; błąd DNS/certyfikatu nie cofa zdrowego kontenera.
Symlink `current` wskazuje ostatnie pomyślne lokalne wdrożenie.

Ręczne przywrócenie zachowanego SHA na VPS:

```sh
bash ~/train-assistant/releases/SHA/deploy.sh SHA
```

Diagnostyka na VPS:

```sh
sudo docker ps --filter label=com.docker.compose.project=train-assistant
sudo docker logs --tail 100 train-assistant-app-1
curl -fsS http://127.0.0.1:18080/healthz
sudo nginx -t
```

Logi kontenera mają rotację 3 × 10 MB; limit pamięci to 512 MB. Aplikacja
działa jako użytkownik bez roota, z systemem plików tylko do odczytu i osobnym
tymczasowym `/tmp`. Wdrożenie jednej instancji powoduje krótką przerwę.
Stare wydania i obrazy są zachowywane do rollbacku; przy porządkach usuwaj
wyłącznie stare wydania Train Assistant, zachowując bieżące i poprzednie.
Nie używaj globalnego `docker system prune` na współdzielonym VPS-ie.

Kontener uruchamia Javę bezpośrednio jako PID 1. Nie dodawaj `init: true`:
instalacja Docker przez Snap na tym VPS-ie blokuje wykonanie `docker-init`.
