import { loadStations, saveStations } from './storage.js';
import { initStationPicker } from './station-picker.js';
import { initJourney } from './journey.js';

const stations = loadStations();
const journey = initJourney(() => stations);
document.getElementById('settings').open = !(stations.first && stations.second);

for (const key of Object.keys(stations)) {
  initStationPicker(key, stations[key], (station) => {
    stations[key] = station;
    const status = document.getElementById('saved');
    try {
      saveStations(stations);
      status.textContent = 'Zapisane. Przy następnej wizycie stacje już tu będą.';
    } catch {
      status.textContent = 'Nie udało się zapisać stacji. Możesz korzystać z nich w tej karcie.';
    }
    journey.updateRoute();
  });
}
