import { searchConnections } from './api.js';
import { renderConnections } from './results.js';

const $ = (id) => document.getElementById(id);

export function initJourney(getStations) {
  let reversed = false;
  let connectionRequest;

  function localDate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
  const today = new Date();
  $('date').min = localDate(today);
  const nextJourney = new Date(today);
  if (today.getHours() >= 8) nextJourney.setDate(nextJourney.getDate() + 1);
  $('date').value = localDate(nextJourney);

  function clearResults() {
    connectionRequest?.abort();
    connectionRequest = null;
    $('search').disabled = false;
    $('search').textContent = 'Znajdź pociąg';
    $('results').setAttribute('aria-busy', 'false');
    $('connections').replaceChildren();
    $('message').textContent = '';
  }
  function getRoute() {
    const stations = getStations();
    return reversed
      ? { origin: stations.second, destination: stations.first }
      : { origin: stations.first, destination: stations.second };
  }

  function updateRoute() {
    clearResults();
    const { origin, destination } = getRoute();
    $('route-from').textContent = origin?.name || 'Wybierz stację';
    $('route-to').textContent = destination?.name || 'Wybierz stację';
    $('swap-direction').disabled = !(origin && destination);
  }

  $('swap-direction').addEventListener('click', () => {
    reversed = !reversed;
    updateRoute();
  });
  for (const id of ['date', 'time']) $(id).addEventListener('input', clearResults);
  for (const option of document.querySelectorAll('input[name="mode"]')) {
    option.addEventListener('change', clearResults);
  }
  updateRoute();

  $('journey').addEventListener('submit', async (event) => {
    event.preventDefault();
    clearResults();
    const stations = getStations();
    const message = $('message');
    message.className = 'error';
    if (!stations.first || !stations.second) {
      message.textContent = 'Wybierz dwie domyślne stacje z podpowiedzi.';
      $('settings').open = true;
      $(!stations.first ? 'first' : 'second').focus();
      return;
    }
    if (stations.first.id === stations.second.id) {
      message.textContent = 'Wybierz dwie różne stacje.';
      return;
    }
    const arrivalBefore = `${$('date').value}T${$('time').value}`;
    if (new Date(arrivalBefore) <= new Date()) {
      message.textContent = 'Ta godzina już minęła. Wybierz późniejszą godzinę lub kolejny dzień.';
      return;
    }
    const { origin, destination } = getRoute();
    const controller = new AbortController();
    connectionRequest = controller;
    $('search').disabled = true;
    $('search').textContent = 'Szukam…';
    $('results').setAttribute('aria-busy', 'true');
    message.className = '';
    message.textContent = 'Sprawdzam połączenia…';
    try {
      const data = await searchConnections(
        origin.name,
        destination.name,
        arrivalBefore,
        document.querySelector('input[name="mode"]:checked').value,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      message.textContent = data.proposals.length ? 'Możesz pojechać tak:' : 'Nie znaleziono bezpośredniego pociągu. Spróbuj innej godziny lub daty.';
      renderConnections(data.proposals, $('date').value, $('connections'));
    } catch (error) {
      if (!controller.signal.aborted) {
        message.className = 'error';
        message.textContent = error.message;
      }
    } finally {
      if (connectionRequest === controller) {
        $('search').disabled = false;
        $('search').textContent = 'Znajdź pociąg';
        $('results').setAttribute('aria-busy', 'false');
      }
    }
  });

  return { updateRoute };
}
