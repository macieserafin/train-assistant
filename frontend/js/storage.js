const storageKey = 'train-assistant-stations';

export function loadStations() {
  const stations = { first: null, second: null };
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey));
    const values = {
      first: saved?.first ?? saved?.home,
      second: saved?.second ?? saved?.university,
    };
    for (const key of Object.keys(stations)) {
      if (typeof values[key]?.name === 'string' && Number.isInteger(values[key].id)) {
        stations[key] = values[key];
      }
    }
  } catch {}
  return stations;
}

export function saveStations(stations) {
  localStorage.setItem(storageKey, JSON.stringify(stations));
}
