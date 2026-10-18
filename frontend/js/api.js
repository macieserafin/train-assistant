async function getJson(path, signal) {
  const response = await fetch(path, { signal });
  if (!response.ok) throw new Error('Nie udało się pobrać danych. Spróbuj ponownie za chwilę.');
  return response.json();
}

export function searchStations(query, signal) {
  return getJson(`/api/stations?${new URLSearchParams({ search: query, pageSize: '100' })}`, signal);
}

export function searchConnections(from, to, arrivalBefore, mode, signal) {
  return getJson(`/api/connections?${new URLSearchParams({ from, to, arrivalBefore, mode })}`, signal);
}
