import { searchStations } from './api.js';

const $ = (id) => document.getElementById(id);

export function initStationPicker(key, selectedStation, onChange) {
  const input = $(key);
  input.value = selectedStation?.name || '';
  let timer;
  let request;
  input.addEventListener('input', () => {
    clearTimeout(timer);
    request?.abort();
    onChange(null);
    const options = $(`${key}-options`);
    const status = $(`${key}-status`);
    options.replaceChildren();
    status.textContent = '';
    const query = input.value.trim();
    if (query.length < 2) return;
    request = new AbortController();
    const signal = request.signal;
    timer = setTimeout(async () => {
      status.textContent = 'Szukam stacji…';
      try {
        const data = await searchStations(query, signal);
        if (signal.aborted) return;
        status.textContent = data.stations.length ? (data.hasMore ? 'Dopisz nazwę, aby zawęzić listę.' : 'Wybierz stację z listy.') : 'Brak stacji. Spróbuj wpisać krótszą nazwę.';
        for (const station of data.stations) {
          const button = document.createElement('button');
          button.type = 'button';
          button.textContent = station.name;
          button.addEventListener('click', () => {
            input.value = station.name;
            options.replaceChildren();
            status.textContent = 'Wybrana stacja.';
            onChange(station);
            input.focus();
          });
          options.append(button);
        }
      } catch (error) {
        if (!signal.aborted) status.textContent = error.message;
      }
    }, 300);
  });
}
